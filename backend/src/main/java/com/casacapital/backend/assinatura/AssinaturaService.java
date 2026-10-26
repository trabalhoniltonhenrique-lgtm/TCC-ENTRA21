package com.casacapital.backend.assinatura;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.fatura.FaturaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Encontra assinaturas nas despesas: o que é cobrado uma vez por mês, todo mês, com valor parecido
 * (padaria, mercado e Uber ficam de fora por aparecerem várias vezes no mês ou com valores muito diferentes),
 * mais serviços de assinatura conhecidos mesmo quando só apareceram uma vez.
 */
@Service
public class AssinaturaService {

    private static final int MESES_ANALISADOS = 12;
    private static final int DIAS_ATIVA = 45;
    private static final BigDecimal VARIACAO_MAXIMA = new BigDecimal("1.6"); // maior valor / menor valor
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    // Comparados com a chave do estabelecimento ("NETFLIX COM", "EBW SPOTIFY"…)
    private static final List<String> SERVICOS_CONHECIDOS = List.of(
            "NETFLIX", "SPOTIFY", "DISNEY", "HBO", "MAX COM", "PRIME VIDEO", "AMAZON PRIME", "AMAZONPRIME",
            "GLOBOPLAY", "DEEZER", "YOUTUBE", "APPLE COM", "ICLOUD", "GOOGLE ONE", "GOOGLE STORAGE", "MICROSOFT",
            "XBOX", "PLAYSTATION", "PATREON", "CAPCUT", "ADOBE", "CANVA", "CHATGPT", "OPENAI", "DUOLINGO",
            "SMART FIT", "SMARTFIT", "BLUEFIT", "TOTALPASS", "WELLHUB", "GYMPASS", "IFOOD CLUB", "UBER ONE",
            "PARAMOUNT", "CRUNCHYROLL", "TWITCH", "MUBI", "TELECINE", "AUDIBLE", "KINDLE", "DROPBOX", "NOTION");

    private final DespesaRepository despesas;
    private final AssinaturaIgnoradaRepository ignoradas;
    private final FamiliaRepository familias;

    public AssinaturaService(DespesaRepository despesas, AssinaturaIgnoradaRepository ignoradas, FamiliaRepository familias) {
        this.despesas = despesas;
        this.ignoradas = ignoradas;
        this.familias = familias;
    }

    @Transactional(readOnly = true)
    public AssinaturasResponse listar(Long familiaId, LocalDate hoje) {
        Set<String> ignorar = new HashSet<>();
        ignoradas.findAllByFamiliaId(familiaId).forEach(i -> ignorar.add(i.getChave()));
        LocalDate inicio = YearMonth.from(hoje).minusMonths(MESES_ANALISADOS - 1L).atDay(1);

        // agrupa por estabelecimento; parcelas ("Geladeira (3/10)") não são assinaturas
        Map<String, List<Despesa>> porLoja = new LinkedHashMap<>();
        for (Despesa d : despesas.findAllByFamiliaIdOrderByDataDesc(familiaId)) {
            if (d.getData().isBefore(inicio) || d.getData().isAfter(hoje)) continue;
            if (d.getNome() == null || d.getNome().matches(".*\\(\\d+/\\d+\\)\\s*$")) continue;
            String chave = FaturaService.chaveLoja(d.getNome());
            if (chave.length() < 3 || ignorar.contains(chave)) continue;
            porLoja.computeIfAbsent(chave, k -> new ArrayList<>()).add(d);
        }

        List<AssinaturasResponse.Assinatura> lista = new ArrayList<>();
        porLoja.forEach((chave, cobrancas) -> {
            AssinaturasResponse.Assinatura a = avaliar(chave, cobrancas, hoje);
            if (a != null) lista.add(a);
        });
        lista.sort(Comparator.comparing(AssinaturasResponse.Assinatura::ativa).reversed()
                .thenComparing(AssinaturasResponse.Assinatura::valor, Comparator.reverseOrder()));

        BigDecimal mensal = lista.stream().filter(AssinaturasResponse.Assinatura::ativa)
                .map(AssinaturasResponse.Assinatura::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AssinaturasResponse(mensal.setScale(2, RoundingMode.HALF_UP),
                mensal.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP), lista,
                ignorar.stream().sorted().toList());
    }

    private static AssinaturasResponse.Assinatura avaliar(String chave, List<Despesa> cobrancas, LocalDate hoje) {
        cobrancas.sort(Comparator.comparing(Despesa::getData));
        TreeMap<YearMonth, List<Despesa>> porMes = new TreeMap<>();
        cobrancas.forEach(d -> porMes.computeIfAbsent(YearMonth.from(d.getData()), k -> new ArrayList<>()).add(d));

        boolean conhecida = SERVICOS_CONHECIDOS.stream().anyMatch(chave::contains);
        BigDecimal menor = cobrancas.stream().map(Despesa::getValor).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal maior = cobrancas.stream().map(Despesa::getValor).max(BigDecimal::compareTo).orElseThrow();

        boolean repete;
        if (porMes.size() >= 2) {
            long mesesComUma = porMes.values().stream().filter(l -> l.size() == 1).count();
            boolean umaPorMes = mesesComUma * 4 >= porMes.size() * 3L; // em pelo menos 75% dos meses, uma cobrança só
            boolean valorEstavel = menor.signum() > 0
                    && maior.divide(menor, 4, RoundingMode.HALF_UP).compareTo(VARIACAO_MAXIMA) <= 0;
            boolean semBuracos = true; // no máximo um mês sem cobrança entre duas cobranças
            YearMonth anterior = null;
            for (YearMonth m : porMes.keySet()) {
                if (anterior != null && ChronoUnit.MONTHS.between(anterior, m) > 2) semBuracos = false;
                anterior = m;
            }
            repete = umaPorMes && valorEstavel && semBuracos;
        } else {
            repete = false;
        }
        boolean provavel = !repete && conhecida && cobrancas.size() == 1;
        if (!repete && !provavel) return null;

        Despesa ultima = cobrancas.get(cobrancas.size() - 1);
        Despesa penultima = cobrancas.size() >= 2 ? cobrancas.get(cobrancas.size() - 2) : null;
        BigDecimal aumento = null;
        if (penultima != null && penultima.getValor().signum() > 0
                && ultima.getValor().compareTo(penultima.getValor().multiply(new BigDecimal("1.01"))) > 0) {
            aumento = ultima.getValor().subtract(penultima.getValor()).multiply(CEM)
                    .divide(penultima.getValor(), 1, RoundingMode.HALF_UP);
        }
        LocalDate primeira = cobrancas.get(0).getData();
        boolean ativa = !ultima.getData().isBefore(hoje.minusDays(DIAS_ATIVA));
        boolean nova = !primeira.isBefore(hoje.minusDays(DIAS_ATIVA));
        return new AssinaturasResponse.Assinatura(chave, ultima.getNome(), ultima.getCategoria(), ultima.getValor(),
                penultima != null ? penultima.getValor() : null, aumento, primeira, ultima.getData(), porMes.size(),
                ativa, nova, provavel);
    }

    @Transactional
    public void ignorar(Long familiaId, String chave) {
        String c = validar(chave);
        if (ignoradas.findByFamiliaIdAndChave(familiaId, c).isPresent()) return;
        AssinaturaIgnorada i = new AssinaturaIgnorada();
        i.setFamilia(familias.getReferenceById(familiaId));
        i.setChave(c);
        ignoradas.save(i);
    }

    @Transactional
    public void desfazerIgnorar(Long familiaId, String chave) {
        ignoradas.findByFamiliaIdAndChave(familiaId, validar(chave)).ifPresent(ignoradas::delete);
    }

    private static String validar(String chave) {
        if (chave == null || chave.isBlank() || chave.length() > 40) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHAVE_INVALIDA", "Assinatura inválida.");
        }
        return chave.trim();
    }
}
