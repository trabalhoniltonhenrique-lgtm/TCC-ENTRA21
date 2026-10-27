package com.casacapital.backend.nfce;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.Categorias;
import com.casacapital.backend.despesa.DespesaService;
import com.casacapital.backend.despesa.dto.DespesaRequest;
import com.casacapital.backend.dispensa.DispensaService;
import com.casacapital.backend.dispensa.dto.ItemDispensaRequest;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.historicoprecos.HistoricoPreco;
import com.casacapital.backend.historicoprecos.HistoricoPrecoService;
import com.casacapital.backend.nfce.dto.NfceImportarRequest;
import com.casacapital.backend.nfce.dto.NfceImportarResponse;
import com.casacapital.backend.nfce.dto.NfceLeituraResponse;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Nota fiscal do mercado (NFC-e) pelo QR code: lê a foto (ou o link), abre a consulta no site da Fazenda,
 * e depois da revisão grava o preço de cada item no Histórico de Preços, dá entrada na Despensa e lança
 * a despesa com o total.
 */
@Service
public class NfceService {

    /** Mesmas categorias da despensa/compras no front-end (js/dispensa.js). */
    static final Set<String> CATEGORIAS_DESPENSA = Set.of(
            "Geral", "Hortifruti", "Laticínios", "Carnes", "Bebidas", "Higiene", "Limpeza", "Frios", "Padaria", "Outros");

    /** Palavras do nome do produto (sem acento, minúsculas) que sugerem a categoria. A ordem importa. */
    private static final Map<String, List<String>> PALAVRAS = new LinkedHashMap<>();

    static {
        PALAVRAS.put("Limpeza", List.of("detergente", "sabao", "amaciante", "desinfetante", "agua sanitaria", "alvejante",
                "limpador", "multiuso", "esponja", "lustra", "saco lixo", "saco p/ lixo", "vassoura", "pano chao", "cloro", "lava roupa", "veja", "omo"));
        PALAVRAS.put("Higiene", List.of("sabonete", "shampoo", "xampu", "condicionador", "creme dental", "pasta dental",
                "escova dental", "fio dental", "papel hig", "desodorante", "absorvente", "fralda", "cotonete", "barbear", "hidratante", "enxaguante"));
        PALAVRAS.put("Bebidas", List.of("refrig", "refri ", "coca", "guarana", "suco", "cerveja", "cerv ", "agua min", "agua de coco",
                "vinho", "energetico", "cha ", "nectar", "whisky", "vodka", "isotonico"));
        PALAVRAS.put("Laticínios", List.of("leite", "queijo", "iogurte", "manteiga", "requeijao", "creme de leite",
                "leite cond", "nata", "coalhada", "mussarela", "muçarela", "parmesao", "danone", "bebida lactea"));
        PALAVRAS.put("Frios", List.of("presunto", "mortadela", "salame", "apresuntado", "peito peru", "blanquet", "salsicha"));
        PALAVRAS.put("Carnes", List.of("carne", "bovin", "frango", "suin", "linguica", "costela", "alcatra", "patinho",
                "acem", "picanha", "file", "coxa", "sobrecoxa", "peixe", "tilapia", "camarao", "moida", "bisteca", "pernil", "contra file"));
        PALAVRAS.put("Padaria", List.of("pao", "bisnaguinha", "bolo", "torrada", "rosca", "sonho", "croissant", "baguete"));
        PALAVRAS.put("Hortifruti", List.of("banana", "maca", "laranja", "limao", "tomate", "alface", "batata", "cebola",
                "alho", "cenoura", "mamao", "melancia", "melao", "uva", "abacaxi", "manga", "pera", "morango", "repolho",
                "couve", "brocolis", "pepino", "abobrinha", "chuchu", "pimentao", "beterraba", "mandioca", "aipim", "ovo"));
    }

    private static final Set<String> UNIDADES_A_GRANEL = Set.of("KG", "G", "GR", "L", "LT", "ML", "M", "MT");

    private final ConsultaSefaz consulta;
    private final NfceImportadaRepository importadas;
    private final HistoricoPrecoService historico;
    private final DispensaService despensa;
    private final DespesaService despesas;
    private final FamiliaRepository familias;

    public NfceService(ConsultaSefaz consulta, NfceImportadaRepository importadas, HistoricoPrecoService historico,
                       DispensaService despensa, DespesaService despesas, FamiliaRepository familias) {
        this.consulta = consulta;
        this.importadas = importadas;
        this.historico = historico;
        this.despensa = despensa;
        this.despesas = despesas;
        this.familias = familias;
    }

    /**
     * Lê a nota pela foto do QR code ou pelo link e abre a consulta no site da Fazenda; com o texto colado
     * da página (quando o site pede verificação humana), lê o texto. Não grava nada.
     */
    @Transactional(readOnly = true)
    public NfceLeituraResponse ler(Long familiaId, byte[] foto, String link, String texto) {
        String endereco = link != null && !link.isBlank() ? link.trim() : (texto == null || texto.isBlank() ? lerQr(foto) : null);
        if (endereco != null) ConsultaSefaz.validar(endereco);

        NotaFiscalLida nota;
        if (texto != null && !texto.isBlank()) {
            try {
                nota = LeitorPaginaNfce.lerTexto(texto, endereco);
            } catch (IOException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "NFCE_TEXTO_INVALIDO", "Não foi possível ler o texto colado.");
            }
            if (nota.itens().isEmpty()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NFCE_SEM_ITENS",
                        "Não achei os itens no texto colado. Na página da nota, use \"Selecionar tudo\" e \"Copiar\", "
                                + "e cole aqui tudo o que copiou.");
            }
        } else {
            try {
                nota = baixarELer(endereco);
            } catch (ConsultaSefaz.VerificacaoHumanaException e) {
                String chave = LeitorPaginaNfce.chaveDoEndereco(endereco);
                return new NfceLeituraResponse(ConsultaSefaz.normalizar(endereco), true, chave, null, null, null, null,
                        chave != null ? jaImportadaEm(familiaId, chave) : null, List.of());
            }
            if (nota.itens().isEmpty()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NFCE_SEM_ITENS",
                        "A página da nota abriu, mas não deu para ler os itens. O site da Fazenda deste estado usa um "
                                + "formato que o sistema ainda não conhece — envie o link da nota para incluirmos.");
            }
        }
        if (nota.chave() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NFCE_SEM_CHAVE",
                    "Não foi possível achar a chave de acesso da nota (os 44 números). Leia também o QR code ou cole o link.");
        }
        return sugerir(familiaId, nota, endereco != null ? ConsultaSefaz.normalizar(endereco) : null);
    }

    @Transactional
    public NfceImportarResponse importar(Long familiaId, NfceImportarRequest req) {
        if (importadas.findByFamiliaIdAndChave(familiaId, req.chave()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "NFCE_JA_IMPORTADA", "Esta nota fiscal já foi importada.");
        }
        Familia familia = familias.getReferenceById(familiaId);
        LocalDate data = req.dataEmissao() != null ? req.dataEmissao() : LocalDate.now();
        String mercado = req.emitente() != null && !req.emitente().isBlank() ? req.emitente().trim() : null;

        int precos = 0, naDespensa = 0;
        for (NfceImportarRequest.Item item : req.itens()) {
            String nome = item.nome().trim();
            if (item.registrarPreco()) {
                historico.registrar(familia, nome, item.valorUnitario(), data, mercado, gtin(item.codigo()));
                precos++;
            }
            if (item.despensa()) {
                String cat = CATEGORIAS_DESPENSA.contains(item.categoria()) ? item.categoria() : "Geral";
                despensa.adicionar(familiaId, new ItemDispensaRequest(nome,
                        item.qtdDespensa() != null ? item.qtdDespensa() : 1, cat));
                naDespensa++;
            }
        }

        Long despesaId = null;
        if (req.lancarDespesa()) {
            String categoria = req.categoriaDespesa() != null ? req.categoriaDespesa() : "Alimentação";
            Categorias.validar(Categorias.DESPESA, categoria);
            String emitente = req.emitente() != null && !req.emitente().isBlank() ? req.emitente().trim() : "nota fiscal";
            String nome = ("Mercado — " + emitente);
            despesaId = despesas.criar(familiaId, new DespesaRequest(nome.length() > 160 ? nome.substring(0, 160) : nome,
                    req.total(), categoria, data, req.membroId())).getId();
        }

        NfceImportada reg = new NfceImportada();
        reg.setFamilia(familia);
        reg.setChave(req.chave());
        reg.setEmitente(req.emitente());
        reg.setCnpj(req.cnpj());
        reg.setDataEmissao(req.dataEmissao());
        reg.setTotal(req.total());
        reg.setItens(req.itens().size());
        reg.setImportadaEm(LocalDateTime.now());
        importadas.save(reg);

        return new NfceImportarResponse(precos, naDespensa, despesaId);
    }

    // ── Auxiliares ──────────────────────────────────────────────────────

    private String lerQr(byte[] foto) {
        if (foto == null || foto.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NFCE_SEM_FOTO", "Envie a foto do QR code da nota ou cole o link.");
        }
        try {
            return LeitorQrCode.ler(foto).orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "QR_NAO_ENCONTRADO",
                    "Não achei o QR code na foto. Tire outra bem de perto, com o QR code inteiro, reto e sem reflexo."));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGEM_INVALIDA",
                    "Não foi possível abrir a imagem. Envie uma foto em JPG ou PNG.");
        }
    }

    private NotaFiscalLida baixarELer(String endereco) {
        ConsultaSefaz.Pagina pagina = consulta.baixar(endereco);
        try {
            Document doc = LeitorPaginaNfce.abrir(pagina.html(), pagina.endereco());
            NotaFiscalLida nota = LeitorPaginaNfce.ler(doc, endereco);
            if (!nota.itens().isEmpty()) return nota;
            // Alguns estados mostram a nota dentro de um quadro (iframe) da mesma página
            for (Element quadro : doc.select("iframe[src], frame[src]")) {
                String src = quadro.absUrl("src");
                if (src.isBlank()) continue;
                ConsultaSefaz.Pagina interna = consulta.baixar(src);
                NotaFiscalLida n = LeitorPaginaNfce.ler(LeitorPaginaNfce.abrir(interna.html(), interna.endereco()), endereco);
                if (!n.itens().isEmpty()) return n;
            }
            return nota;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "NFCE_CONSULTA_FALHOU", "A página da nota veio com defeito. Tente de novo.");
        }
    }

    private LocalDateTime jaImportadaEm(Long familiaId, String chave) {
        return importadas.findByFamiliaIdAndChave(familiaId, chave).map(NfceImportada::getImportadaEm).orElse(null);
    }

    NfceLeituraResponse sugerir(Long familiaId, NotaFiscalLida nota, String link) {
        LocalDateTime importadaEm = jaImportadaEm(familiaId, nota.chave());
        List<NfceLeituraResponse.Item> itens = nota.itens().stream().map(i -> {
            Optional<HistoricoPreco> anterior = historico.ultimo(familiaId, i.nome());
            return new NfceLeituraResponse.Item(i.nome(), i.codigo(), i.quantidade(), i.unidade(), i.valorUnitario(),
                    i.valorTotal(), categoria(i.nome()), qtdDespensa(i),
                    anterior.map(HistoricoPreco::getPreco).orElse(null),
                    anterior.map(HistoricoPreco::getData).orElse(null));
        }).toList();
        // mesmo CNPJ de uma nota anterior: usa o nome que a família deu ao mercado naquela vez
        String emitente = nota.cnpj() == null ? nota.emitente() : importadas
                .findFirstByFamiliaIdAndCnpjOrderByImportadaEmDesc(familiaId, nota.cnpj())
                .map(NfceImportada::getEmitente).filter(n -> n != null && !n.isBlank()).orElse(nota.emitente());
        return new NfceLeituraResponse(link, false, nota.chave(), emitente, nota.cnpj(), nota.dataEmissao(), nota.total(),
                importadaEm, itens);
    }

    static String categoria(String nomeProduto) {
        String n = " " + Normalizer.normalize(nomeProduto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/ ]", " ").replaceAll("\\s+", " ") + " ";
        for (Map.Entry<String, List<String>> e : PALAVRAS.entrySet()) {
            for (String palavra : e.getValue()) {
                String p = Normalizer.normalize(palavra, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                if (n.contains(" " + p)) return e.getKey();
            }
        }
        return "Geral";
    }

    /** Itens contados (UN, PCT, CX...) entram com a quantidade; produto a granel (kg, litro) conta como 1. */
    static int qtdDespensa(NotaFiscalLida.Item i) {
        if (i.unidade() != null && UNIDADES_A_GRANEL.contains(i.unidade())) return 1;
        int q = i.quantidade().setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(1, Math.min(999, q));
    }

    /**
     * Código de barras (GTIN-8/12/13/14) quando o "código" da nota é um, com dígito verificador certo.
     * Muitos mercados põem ali um código interno, que não serve para comparar com outro mercado.
     */
    static String gtin(String codigo) {
        if (codigo == null) return null;
        String d = codigo.replaceAll("\\D", "");
        if (!(d.length() == 8 || d.length() == 12 || d.length() == 13 || d.length() == 14) || d.chars().allMatch(c -> c == '0')) {
            return null;
        }
        int soma = 0;
        for (int i = d.length() - 2, peso = 3; i >= 0; i--, peso = peso == 3 ? 1 : 3) soma += (d.charAt(i) - '0') * peso;
        int dv = (10 - soma % 10) % 10;
        if (dv != d.charAt(d.length() - 1) - '0') return null;
        return d.length() == 14 ? d : "0".repeat(14 - d.length()) + d; // guarda sempre com 14 dígitos
    }
}
