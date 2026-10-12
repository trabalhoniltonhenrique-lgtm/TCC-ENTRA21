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
import com.casacapital.backend.fatura.LeitorFaturaIA.FaturaExtraida;
import com.casacapital.backend.fatura.LeitorFaturaIA.LancamentoExtraido;
import com.casacapital.backend.fatura.LeitorFaturaIA.TipoArquivo;
import com.casacapital.backend.fatura.dto.FaturaAnaliseResponse;
import com.casacapital.backend.fatura.dto.FaturaImportRequest;
import com.casacapital.backend.fatura.dto.FaturaImportResponse;
import com.casacapital.backend.fatura.dto.ItemFatura;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    private final LeitorFaturaIA leitor;
    private final ContaRepository contaRepository;
    private final DespesaRepository despesaRepository;
    private final FamiliaRepository familiaRepository;

    public FaturaService(LeitorFaturaIA leitor, ContaRepository contaRepository,
                         DespesaRepository despesaRepository, FamiliaRepository familiaRepository) {
        this.leitor = leitor;
        this.contaRepository = contaRepository;
        this.despesaRepository = despesaRepository;
        this.familiaRepository = familiaRepository;
    }

    public boolean disponivel() {
        return leitor.disponivel();
    }

    // ── 1) Leitura + comparação com o que já está cadastrado (não grava nada) ──

    public FaturaAnaliseResponse analisar(Long familiaId, byte[] arquivo, String senha) {
        TipoArquivo tipo = detectarTipo(arquivo);
        byte[] conteudo = tipo == TipoArquivo.PDF ? abrirPdf(arquivo, senha) : arquivo;

        FaturaExtraida fatura = leitor.ler(conteudo, tipo);
        LocalDate vencimento = LeitorFaturaIA.data(fatura.vencimento());
        LocalDate dataPadrao = vencimento != null ? vencimento : LocalDate.now();

        Set<Long> contasUsadas = new HashSet<>();
        Set<Long> despesasUsadas = new HashSet<>();
        List<ItemFatura> itens = new ArrayList<>();
        List<ItemFatura> creditos = new ArrayList<>();

        for (LancamentoExtraido l : fatura.lancamentos() == null ? List.<LancamentoExtraido>of() : fatura.lancamentos()) {
            BigDecimal valor = BigDecimal.valueOf(Math.abs(l.valor())).setScale(2, RoundingMode.HALF_UP);
            if (valor.signum() == 0 || l.descricao() == null || l.descricao().isBlank()) continue;
            String descricao = l.descricao().trim();
            LocalDate dataCompra = LeitorFaturaIA.data(l.data());
            String categoria = Categorias.DESPESA.contains(l.categoria()) ? l.categoria() : "Outros";

            if ("CREDITO".equalsIgnoreCase(l.tipo())) {
                creditos.add(new ItemFatura(dataCompra, descricao, valor, null, null, categoria, null, null, null));
                continue;
            }

            boolean parcelado = l.parcelaTotal() >= 2 && l.parcelaAtual() >= 1 && l.parcelaAtual() <= l.parcelaTotal()
                    && l.parcelaTotal() <= MAX_PARCELAS;
            if (parcelado) {
                itens.add(analisarParcela(familiaId, descricao, valor, l.parcelaAtual(), l.parcelaTotal(),
                        categoria, dataCompra, dataPadrao, contasUsadas));
            } else {
                LocalDate data = dataCompra != null ? dataCompra : dataPadrao;
                itens.add(analisarAVista(familiaId, descricao, valor, data, categoria, despesasUsadas));
            }
        }

        BigDecimal total = fatura.total() > 0
                ? BigDecimal.valueOf(fatura.total()).setScale(2, RoundingMode.HALF_UP) : null;
        return new FaturaAnaliseResponse(fatura.emissor(), vencimento, total, itens, creditos);
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

    private static TipoArquivo detectarTipo(byte[] b) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return TipoArquivo.PDF;
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return TipoArquivo.JPEG;
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return TipoArquivo.PNG;
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return TipoArquivo.WEBP;
        throw new ApiException(HttpStatus.BAD_REQUEST, "ARQUIVO_INVALIDO",
                "Envie a fatura em PDF ou uma foto (JPG, PNG ou WEBP).");
    }

    /** Abre o PDF (com a senha, se houver) e devolve uma cópia sem proteção para a IA conseguir ler. */
    private static byte[] abrirPdf(byte[] arquivo, String senha) {
        try (PDDocument doc = senha == null || senha.isEmpty() ? Loader.loadPDF(arquivo) : Loader.loadPDF(arquivo, senha)) {
            if (doc.getNumberOfPages() > MAX_PAGINAS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FATURA_GRANDE_DEMAIS",
                        "O PDF tem páginas demais. Envie só as páginas da fatura.");
            }
            if (!doc.isEncrypted()) return arquivo;
            doc.setAllSecurityToBeRemoved(true);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            return saida.toByteArray();
        } catch (InvalidPasswordException e) {
            boolean semSenha = senha == null || senha.isEmpty();
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, semSenha ? "PDF_PROTEGIDO" : "SENHA_INCORRETA",
                    semSenha ? "Este PDF tem senha. Informe a senha da fatura (geralmente parte do seu CPF)."
                            : "Senha do PDF incorreta.");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PDF_INVALIDO", "Não foi possível abrir este PDF.");
        }
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
