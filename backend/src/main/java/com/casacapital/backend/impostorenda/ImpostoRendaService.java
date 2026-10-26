package com.casacapital.backend.impostorenda;

import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.investimento.Investimento;
import com.casacapital.backend.investimento.InvestimentoRepository;
import com.casacapital.backend.receita.Receita;
import com.casacapital.backend.receita.ReceitaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/** Junta o que o sistema já tem do ano: rendimentos, despesas de saúde e educação e os investimentos. */
@Service
public class ImpostoRendaService {

    static final String PROVAVEL = "PROVAVEL", NAO = "NAO", CONFERIR = "CONFERIR";
    private static final String SEM_PESSOA = "Sem pessoa definida";

    // Saúde: farmácia, remédio e ótica em geral não são dedutíveis; consultas, exames, hospitais e planos são
    private static final List<String> SAUDE_NAO = List.of("DROGARIA", "DROGA ", "FARMA", "RAIA", "DROGASIL", "PANVEL",
            "PAGUE MENOS", "PACHECO", "SAO JOAO", "NISSEI", "REMEDIO", "OTICA", "OCULOS", "LENTE");
    private static final List<String> SAUDE_SIM = List.of("PLANO", "UNIMED", "AMIL", "HAPVIDA", "SULAMERICA", "BRADESCO SAUDE",
            "NOTREDAME", "HOSPITAL", "CLINICA", "MEDIC", "DOUTOR", "DR ", "DRA ", "DENTIST", "ODONTO", "LABORATORIO", "EXAME",
            "PSICOLOG", "PSIQUIATR", "FISIOTERAP", "FONOAUDIOLOG", "TERAPIA OCUPACIONAL", "CONSULTA");
    // Educação: escola e faculdade (com limite anual por pessoa); cursos livres, idiomas e material não
    private static final List<String> EDU_NAO = List.of("CURSO", "IDIOMA", "INGLES", "DUOLINGO", "UDEMY", "ALURA", "COURSERA",
            "LIVRARIA", "PAPELARIA", "KALUNGA", "MATERIAL", "UNIFORME", "TRANSPORTE ESCOLAR", "VAN ");
    private static final List<String> EDU_SIM = List.of("ESCOLA", "COLEGIO", "FACULDADE", "UNIVERSIDADE", "CRECHE",
            "PRE ESCOLA", "ENSINO", "INSTITUTO FEDERAL", "POS GRADUACAO", "MESTRADO", "DOUTORADO", "MENSALIDADE");

    private final ReceitaRepository receitas;
    private final DespesaRepository despesas;
    private final InvestimentoRepository investimentos;

    public ImpostoRendaService(ReceitaRepository receitas, DespesaRepository despesas, InvestimentoRepository investimentos) {
        this.receitas = receitas;
        this.despesas = despesas;
        this.investimentos = investimentos;
    }

    @Transactional(readOnly = true)
    public ImpostoRendaResponse gerar(Long familiaId, int ano) {
        List<Receita> recs = receitas.findAllByFamiliaIdOrderByDataDesc(familiaId);
        List<Despesa> desps = despesas.findAllByFamiliaIdOrderByDataDesc(familiaId);

        TreeSet<Integer> anos = new TreeSet<>(Comparator.reverseOrder());
        recs.forEach(r -> anos.add(r.getData().getYear()));
        desps.forEach(d -> anos.add(d.getData().getYear()));

        List<Receita> recAno = recs.stream().filter(r -> r.getData().getYear() == ano).toList();
        ImpostoRendaResponse.Rendimentos rendimentos = new ImpostoRendaResponse.Rendimentos(
                soma(recAno.stream().map(Receita::getValor).toList()),
                totais(recAno, Receita::getCategoria, Receita::getValor),
                totais(recAno, r -> r.getMembro() != null ? r.getMembro().getNome() : SEM_PESSOA, Receita::getValor));

        List<Despesa> despAno = desps.stream().filter(d -> d.getData().getYear() == ano)
                .sorted(Comparator.comparing(Despesa::getData)).toList();
        ImpostoRendaResponse.Dedutiveis saude = dedutiveis(despAno.stream().filter(d -> "Saúde".equals(d.getCategoria())).toList(), true);
        ImpostoRendaResponse.Dedutiveis educacao = dedutiveis(despAno.stream().filter(d -> "Educação".equals(d.getCategoria())).toList(), false);

        LocalDate fimDoAno = LocalDate.of(ano, 12, 31);
        List<ImpostoRendaResponse.Bem> bens = investimentos.findAllByFamiliaId(familiaId).stream()
                .filter(i -> i.getDataAplicacao() == null || !i.getDataAplicacao().isAfter(fimDoAno))
                .map(i -> new ImpostoRendaResponse.Bem(i.getNome(), i.getTipo(), i.getInstituicao(), i.getDataAplicacao(),
                        i.getValorAplicado(), i.getValorAtual()))
                .toList();

        return new ImpostoRendaResponse(ano, new ArrayList<>(anos), rendimentos, saude, educacao, bens);
    }

    private static ImpostoRendaResponse.Dedutiveis dedutiveis(List<Despesa> lista, boolean ehSaude) {
        List<ImpostoRendaResponse.Item> itens = lista.stream().map(d -> {
            String[] s = ehSaude ? classificarSaude(d.getNome()) : classificarEducacao(d.getNome());
            return new ImpostoRendaResponse.Item(d.getData(), d.getNome(),
                    d.getMembro() != null ? d.getMembro().getNome() : SEM_PESSOA, d.getValor(), s[0], s[1]);
        }).toList();
        BigDecimal provavel = soma(itens.stream().filter(i -> PROVAVEL.equals(i.situacao())).map(ImpostoRendaResponse.Item::valor).toList());
        return new ImpostoRendaResponse.Dedutiveis(soma(itens.stream().map(ImpostoRendaResponse.Item::valor).toList()), provavel,
                totais(itens, ImpostoRendaResponse.Item::pessoa, ImpostoRendaResponse.Item::valor), itens);
    }

    static String[] classificarSaude(String nome) {
        String n = normalizar(nome);
        if (contem(n, SAUDE_NAO)) return new String[]{NAO, "Farmácia, remédio e ótica em geral não são dedutíveis"};
        if (contem(n, SAUDE_SIM)) return new String[]{PROVAVEL, "Consulta, exame, hospital ou plano de saúde"};
        return new String[]{CONFERIR, "Confira se é despesa médica com recibo/nota em nome do prestador"};
    }

    static String[] classificarEducacao(String nome) {
        String n = normalizar(nome);
        if (contem(n, EDU_NAO)) return new String[]{NAO, "Cursos livres, idiomas e material escolar não são dedutíveis"};
        if (contem(n, EDU_SIM)) return new String[]{PROVAVEL, "Ensino regular (há limite anual por pessoa)"};
        return new String[]{CONFERIR, "Confira se é ensino regular (escola, faculdade, técnico)"};
    }

    private static <T> List<ImpostoRendaResponse.Total> totais(List<T> lista, Function<T, String> chave, Function<T, BigDecimal> valor) {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        lista.forEach(x -> m.merge(chave.apply(x), valor.apply(x), BigDecimal::add));
        return m.entrySet().stream().map(e -> new ImpostoRendaResponse.Total(e.getKey(), e.getValue().setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(ImpostoRendaResponse.Total::valor).reversed()).toList();
    }

    private static boolean contem(String texto, List<String> palavras) {
        String t = " " + texto + " ";
        return palavras.stream().anyMatch(t::contains);
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static BigDecimal soma(List<BigDecimal> l) {
        return l.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
}
