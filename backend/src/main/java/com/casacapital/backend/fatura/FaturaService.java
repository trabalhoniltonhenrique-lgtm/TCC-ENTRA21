package com.casacapital.backend.fatura;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.Categorias;
import com.casacapital.backend.conta.Conta;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.conta.ContaService;
import com.casacapital.backend.conta.Recorrencia;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.fatura.LeitorContaConsumo.ContaLida;
import com.casacapital.backend.fatura.LeitorFaturaPdf.FaturaLida;
import com.casacapital.backend.fatura.LeitorFaturaPdf.Lancamento;
import com.casacapital.backend.fatura.dto.ContaConsumoImportRequest;
import com.casacapital.backend.fatura.dto.FaturaAnaliseResponse;
import com.casacapital.backend.fatura.dto.FaturaImportRequest;
import com.casacapital.backend.fatura.dto.FaturaImportResponse;
import com.casacapital.backend.fatura.dto.ItemFatura;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class FaturaService {

    static final String NOVA_DESPESA = "NOVA_DESPESA";
    static final String NOVA_PARCELADA = "NOVA_PARCELADA";
    static final String QUITAR_PARCELA = "QUITAR_PARCELA";
    static final String JA_IMPORTADA = "JA_IMPORTADA";

    private static final String CATEGORIA_CONTA = "Cartão de Crédito";
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.02");
    private static final int MAX_PAGINAS = 60;
    private static final int MAX_PARCELAS = 99;

    private final ContaRepository contaRepository;
    private final DespesaRepository despesaRepository;
    private final FamiliaRepository familiaRepository;

    public FaturaService(ContaRepository contaRepository, DespesaRepository despesaRepository,
                         FamiliaRepository familiaRepository) {
        this.contaRepository = contaRepository;
        this.despesaRepository = despesaRepository;
        this.familiaRepository = familiaRepository;
    }

    // ── 1) Leitura + comparação com o que já está cadastrado (não grava nada) ──

    public FaturaAnaliseResponse analisar(Long familiaId, byte[] arquivo, String senha) {
        List<List<String>> texto = extrairTexto(arquivo, senha);

        // Conta de luz/água: vira uma conta a pagar, não uma lista de lançamentos
        Optional<ContaLida> consumo = LeitorContaConsumo.ler(texto.get(0));
        if (consumo.isPresent()) return analisarContaConsumo(familiaId, consumo.get());

        FaturaLida fatura = LeitorFaturaPdf.lerMelhor(texto.get(0), texto.get(1), LocalDate.now());
        if (fatura.lancamentos().isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SEM_LANCAMENTOS",
                    "Não encontrei lançamentos neste PDF. Use o PDF original baixado do app ou site do banco "
                            + "(PDF escaneado ou foto não funciona).");
        }
        LocalDate vencimento = fatura.vencimento();
        LocalDate dataPadrao = vencimento != null ? vencimento : LocalDate.now();

        Set<Long> contasUsadas = new HashSet<>();
        Set<Long> despesasUsadas = new HashSet<>();
        List<ItemFatura> itens = new ArrayList<>();
        List<ItemFatura> creditos = new ArrayList<>();

        for (Lancamento l : fatura.lancamentos()) {
            if (l.credito()) {
                creditos.add(new ItemFatura(l.data(), l.descricao(), l.valor(), null, null, l.categoria(), null, null, null));
            } else if (l.parcelaTotal() >= 2 && l.parcelaTotal() <= MAX_PARCELAS) {
                itens.add(analisarParcela(familiaId, l.descricao(), l.valor(), l.parcelaAtual(), l.parcelaTotal(),
                        l.categoria(), l.data(), dataPadrao, contasUsadas));
            } else {
                itens.add(analisarAVista(familiaId, l.descricao(), l.valor(), l.data(), l.categoria(), despesasUsadas));
            }
        }

        return FaturaAnaliseResponse.cartao(fatura.emissor(), vencimento, fatura.total(), fatura.totalLancamentos(),
                itens, creditos);
    }

    private FaturaAnaliseResponse analisarContaConsumo(Long familiaId, ContaLida lida) {
        if (lida.valor() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SEM_VALOR",
                    "Reconheci a conta de " + lida.categoria().toLowerCase() + ", mas não encontrei o valor a pagar. "
                            + "Cadastre-a em Nova Conta.");
        }
        Long contaId = null;
        String contaExistente = null;
        boolean jaCadastrada = false;
        if (lida.vencimento() != null) {
            for (Conta c : contaRepository.findAllByFamiliaIdOrderByVencimentoAsc(familiaId)) {
                if (!lida.categoria().equals(c.getCategoria())) continue;
                if (c.getVencimento().equals(lida.vencimento()) && c.getValor().compareTo(lida.valor()) == 0) {
                    jaCadastrada = true;
                    contaId = null;
                    contaExistente = c.getDescricao();
                    break;
                }
                // conta em aberto da mesma categoria no mesmo mês (ex.: a mensal com valor estimado)
                if (!c.isPago() && contaId == null && c.getGrupoParcelaId() == null
                        && YearMonth.from(c.getVencimento()).equals(YearMonth.from(lida.vencimento()))) {
                    contaId = c.getId();
                    contaExistente = c.getDescricao();
                }
            }
        }
        return FaturaAnaliseResponse.contaConsumo(new FaturaAnaliseResponse.ContaConsumo(
                lida.descricao(), lida.categoria(), lida.valor(), lida.vencimento(), lida.referencia(),
                contaId, contaExistente, jaCadastrada));
    }

    /** Cadastra a conta de luz/água revisada, ou atualiza valor e vencimento da conta em aberto indicada. */
    @Transactional
    public Conta importarContaConsumo(Long familiaId, ContaConsumoImportRequest req) {
        if (req.contaId() != null) {
            Conta c = contaRepository.findByIdAndFamiliaId(req.contaId(), familiaId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CONTA_NAO_ENCONTRADA",
                            "A conta a atualizar não existe mais. Leia a fatura de novo."));
            if (c.isPago()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CONTA_JA_PAGA",
                        "A conta \"" + c.getDescricao() + "\" já foi paga. Leia a fatura de novo.");
            }
            c.setValor(req.valor());
            c.setVencimento(req.vencimento());
            return c;
        }
        Conta c = new Conta();
        c.setFamilia(familiaRepository.getReferenceById(familiaId));
        c.setDescricao(limitar(req.descricao().trim(), 160));
        c.setCategoria(limitar(req.categoria().trim(), 40));
        c.setValor(req.valor());
        c.setVencimento(req.vencimento());
        c.setRecorrencia(Recorrencia.UNICA);
        c.setObs("Importado da fatura em PDF");
        return contaRepository.save(c);
    }

    private ItemFatura analisarParcela(Long familiaId, String descricao, BigDecimal valor, int atual, int total,
                                       String categoria, LocalDate dataCompra, LocalDate dataPadrao,
                                       Set<Long> contasUsadas) {
        // A 1ª parcela entra na data da compra; as seguintes, no vencimento desta fatura.
        LocalDate data = atual == 1 && dataCompra != null ? dataCompra : dataPadrao;

        Conta pendente = null;
        Conta paga = null;
        for (Conta c : contaRepository.findAllByFamiliaIdAndParcelaNumAndParcelaTotal(familiaId, atual, total)) {
            if (contasUsadas.contains(c.getId()) || !valorProximo(c.getValor(), valor)
                    || !mesmaLoja(descricaoBase(c.getDescricao()), descricao)) continue;
            if (!c.isPago() && pendente == null) pendente = c;
            if (c.isPago() && paga == null) paga = c;
        }

        if (pendente != null) {
            contasUsadas.add(pendente.getId());
            return new ItemFatura(data, descricao, valor, atual, total, categoria, QUITAR_PARCELA, pendente.getId(),
                    "Parcela já cadastrada em Contas — será marcada como paga.");
        }
        if (paga != null) {
            contasUsadas.add(paga.getId());
            return new ItemFatura(data, descricao, valor, atual, total, categoria, JA_IMPORTADA, null,
                    "Esta parcela já consta como paga em Contas.");
        }
        String aviso = atual < total
                ? "Cria as parcelas " + (atual + 1) + " a " + total + " em Contas a Pagar."
                : "Última parcela.";
        return new ItemFatura(data, descricao, valor, atual, total, categoria, NOVA_PARCELADA, null, aviso);
    }

    private ItemFatura analisarAVista(Long familiaId, String descricao, BigDecimal valor, LocalDate data,
                                      String categoria, Set<Long> despesasUsadas) {
        for (Despesa d : despesaRepository.findAllByFamiliaIdAndData(familiaId, data)) {
            if (despesasUsadas.contains(d.getId()) || !valorProximo(d.getValor(), valor)
                    || !mesmaLoja(d.getNome(), descricao)) continue;
            despesasUsadas.add(d.getId());
            return new ItemFatura(data, descricao, valor, null, null, categoria, JA_IMPORTADA, null,
                    "Já existe uma despesa igual nesta data.");
        }
        return new ItemFatura(data, descricao, valor, null, null, categoria, NOVA_DESPESA, null, null);
    }

    // ── 2) Gravação dos itens que o usuário confirmou na revisão ──

    @Transactional
    public FaturaImportResponse importar(Long familiaId, FaturaImportRequest req) {
        Familia familia = familiaRepository.getReferenceById(familiaId);
        int despesas = 0, parcelamentos = 0, quitadas = 0;
        BigDecimal total = BigDecimal.ZERO;
        long grupoBase = System.currentTimeMillis();
        String cartao = req.cartao() == null || req.cartao().isBlank() ? null : req.cartao().trim();

        for (int i = 0; i < req.itens().size(); i++) {
            FaturaImportRequest.Item item = req.itens().get(i);
            Categorias.validar(Categorias.DESPESA, item.categoria());
            String descricao = limitar(item.descricao().trim(), 150);
            int atual = item.parcelaAtual() != null ? item.parcelaAtual() : 0;
            int qtd = item.parcelaTotal() != null ? item.parcelaTotal() : 0;
            boolean parcelado = qtd >= 2 && atual >= 1 && atual <= qtd;
            if (qtd > MAX_PARCELAS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "QTD_PARCELAS_INVALIDA",
                        "Número de parcelas inválido em \"" + descricao + "\".");
            }

            if (item.contaId() != null) {
                Conta conta = contaRepository.findByIdAndFamiliaId(item.contaId(), familiaId)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CONTA_NAO_ENCONTRADA",
                                "A parcela de \"" + descricao + "\" não existe mais em Contas. Leia a fatura de novo."));
                if (!conta.isPago()) {
                    conta.setCategoriaDespesa(item.categoria());
                    if (conta.getCartao() == null) conta.setCartao(cartao);
                    pagar(conta, item.data(), item.valor());
                    quitadas++;
                    total = total.add(item.valor());
                }
                continue;
            }

            if (parcelado) {
                long grupoId = grupoBase + i;
                for (int n = atual; n <= qtd; n++) {
                    Conta c = new Conta();
                    c.setFamilia(familia);
                    c.setDescricao(descricao + " (" + n + "/" + qtd + ")");
                    c.setCategoria(CATEGORIA_CONTA);
                    c.setCategoriaDespesa(item.categoria());
                    c.setCartao(cartao);
                    c.setValor(item.valor());
                    c.setVencimento(req.vencimento().plusMonths(n - atual));
                    c.setRecorrencia(Recorrencia.PARCELADA);
                    c.setObs("Importado da fatura do cartão");
                    c.setGrupoParcelaId(grupoId);
                    c.setParcelaNum(n);
                    c.setParcelaTotal(qtd);
                    contaRepository.save(c);
                    if (n == atual) pagar(c, item.data(), item.valor()); // a parcela desta fatura já entra paga
                }
                parcelamentos++;
            } else {
                Despesa d = new Despesa();
                d.setFamilia(familia);
                d.setNome(descricao);
                d.setValor(item.valor());
                d.setCategoria(item.categoria());
                d.setData(item.data());
                despesaRepository.save(d);
                despesas++;
            }
            total = total.add(item.valor());
        }

        return new FaturaImportResponse(despesas, parcelamentos, quitadas, total);
    }

    /** Marca a parcela como paga e lança a despesa ligada a ela (mesma regra de ContaService.marcarPago). */
    private void pagar(Conta c, LocalDate data, BigDecimal valor) {
        c.setPago(true);
        c.setDataPagamento(data);
        Despesa d = new Despesa();
        d.setFamilia(c.getFamilia());
        d.setNome(c.getDescricao());
        d.setValor(valor);
        d.setCategoria(ContaService.categoriaDespesa(c));
        d.setData(data);
        d.setOrigemConta(c);
        d.setOrigemContaFlag(true);
        despesaRepository.save(d);
    }

    // ── Arquivo ──

    /** Abre o PDF (com a senha, se houver) e devolve o texto de cada página em duas ordens: a do arquivo e a da posição. */
    private static List<List<String>> extrairTexto(byte[] arquivo, String senha) {
        if (arquivo.length < 5 || arquivo[0] != '%' || arquivo[1] != 'P' || arquivo[2] != 'D' || arquivo[3] != 'F') {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ARQUIVO_INVALIDO", "Envie a fatura em PDF.");
        }
        try (PDDocument doc = senha == null || senha.isEmpty() ? Loader.loadPDF(arquivo) : Loader.loadPDF(arquivo, senha)) {
            int paginas = doc.getNumberOfPages();
            if (paginas > MAX_PAGINAS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FATURA_GRANDE_DEMAIS",
                        "O PDF tem páginas demais. Envie só as páginas da fatura.");
            }
            return List.of(textoPorPagina(doc, false), textoPorPagina(doc, true));
        } catch (InvalidPasswordException e) {
            boolean semSenha = senha == null || senha.isEmpty();
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, semSenha ? "PDF_PROTEGIDO" : "SENHA_INCORRETA",
                    semSenha ? "Este PDF tem senha. Informe a senha da fatura (geralmente parte do seu CPF)."
                            : "Senha do PDF incorreta.");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_INVALIDO", "Não foi possível abrir este PDF.");
        }
    }

    private static List<String> textoPorPagina(PDDocument doc, boolean ordenarPorPosicao) throws IOException {
        PDFTextStripper leitor = new PDFTextStripper();
        leitor.setSortByPosition(ordenarPorPosicao);
        List<String> texto = new ArrayList<>();
        for (int p = 1; p <= doc.getNumberOfPages(); p++) {
            leitor.setStartPage(p);
            leitor.setEndPage(p);
            texto.add(leitor.getText(doc));
        }
        return texto;
    }

    // ── Comparação ──

    private static boolean valorProximo(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCIA) <= 0;
    }

    /** "LOJA X (3/10)" -> "LOJA X" */
    private static String descricaoBase(String descricao) {
        return descricao.replaceFirst("\\s*\\(\\d+/\\d+\\)\\s*$", "");
    }

    /** Mesmo estabelecimento: os primeiros caracteres (sem acento/pontuação) batem. */
    static boolean mesmaLoja(String a, String b) {
        String na = normalizar(a), nb = normalizar(b);
        int k = Math.min(6, Math.min(na.length(), nb.length()));
        return k >= 3 && na.regionMatches(0, nb, 0, k);
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
    }

    private static String limitar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
