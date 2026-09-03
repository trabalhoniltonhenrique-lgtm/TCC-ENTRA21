package com.casacapital.backend.analise;

import com.casacapital.backend.analise.dto.AnaliseResumoResponse;
import com.casacapital.backend.analise.dto.CategoriaValorResponse;
import com.casacapital.backend.analise.dto.SerieMensalResponse;
import com.casacapital.backend.analise.dto.TendenciaCategoriaResponse;
import com.casacapital.backend.common.PremiumRequiredException;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.receita.Receita;
import com.casacapital.backend.receita.ReceitaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cálculos analíticos (gráficos, tendências, projeção) — recurso Premium.
 * Ao contrário das demais telas, aqui os dados agregados só são calculados e
 * devolvidos se a família for Premium; uma família Essencial recebe 403 mesmo
 * acessando o endpoint diretamente.
 */
@Service
public class AnaliseService {

    private static final DateTimeFormatter MES_ANO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ReceitaRepository receitaRepository;
    private final DespesaRepository despesaRepository;
    private final FamiliaRepository familiaRepository;

    public AnaliseService(ReceitaRepository receitaRepository, DespesaRepository despesaRepository,
                           FamiliaRepository familiaRepository) {
        this.receitaRepository = receitaRepository;
        this.despesaRepository = despesaRepository;
        this.familiaRepository = familiaRepository;
    }

    @Transactional(readOnly = true)
    public AnaliseResumoResponse resumo(Long familiaId) {
        var familia = familiaRepository.findById(familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Família não encontrada."));
        if (!familia.isPremium()) {
            throw new PremiumRequiredException();
        }

        List<Receita> receitas = receitaRepository.findAllByFamiliaIdOrderByDataDesc(familiaId);
        List<Despesa> despesas = despesaRepository.findAllByFamiliaIdOrderByDataDesc(familiaId);

        BigDecimal totalReceitas = receitas.stream().map(Receita::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDespesas = despesas.stream().map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoAtual = totalReceitas.subtract(totalDespesas);

        List<SerieMensalResponse> serie = calcularSerieMensal(receitas, despesas);
        List<CategoriaValorResponse> despesasPorCategoria = calcularDespesasPorCategoria(despesas);
        List<TendenciaCategoriaResponse> tendencia = calcularTendencia(despesas);

        return new AnaliseResumoResponse(saldoAtual, serie, despesasPorCategoria, tendencia);
    }

    private List<SerieMensalResponse> calcularSerieMensal(List<Receita> receitas, List<Despesa> despesas) {
        YearMonth atual = YearMonth.now();
        List<SerieMensalResponse> serie = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = atual.minusMonths(i);
            String chave = ym.format(MES_ANO);
            BigDecimal r = receitas.stream()
                    .filter(x -> x.getData() != null && ym.equals(YearMonth.from(x.getData())))
                    .map(Receita::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal d = despesas.stream()
                    .filter(x -> x.getData() != null && ym.equals(YearMonth.from(x.getData())))
                    .map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            serie.add(new SerieMensalResponse(chave, r, d, r.subtract(d)));
        }
        return serie;
    }

    private List<CategoriaValorResponse> calcularDespesasPorCategoria(List<Despesa> despesas) {
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        despesas.forEach(d -> mapa.merge(d.getCategoria(), d.getValor(), BigDecimal::add));
        return mapa.entrySet().stream()
                .map(e -> new CategoriaValorResponse(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.valor().compareTo(a.valor()))
                .toList();
    }

    private List<TendenciaCategoriaResponse> calcularTendencia(List<Despesa> despesas) {
        YearMonth atual = YearMonth.now();
        YearMonth anterior = atual.minusMonths(1);

        Map<String, BigDecimal> mapaAtual = new LinkedHashMap<>();
        Map<String, BigDecimal> mapaAnterior = new LinkedHashMap<>();
        despesas.forEach(d -> {
            if (d.getData() == null) return;
            YearMonth ym = YearMonth.from(d.getData());
            if (ym.equals(atual)) mapaAtual.merge(d.getCategoria(), d.getValor(), BigDecimal::add);
            else if (ym.equals(anterior)) mapaAnterior.merge(d.getCategoria(), d.getValor(), BigDecimal::add);
        });

        List<String> categorias = new ArrayList<>();
        mapaAnterior.keySet().forEach(c -> { if (!categorias.contains(c)) categorias.add(c); });
        mapaAtual.keySet().forEach(c -> { if (!categorias.contains(c)) categorias.add(c); });

        return categorias.stream()
                .map(cat -> new TendenciaCategoriaResponse(cat,
                        mapaAnterior.getOrDefault(cat, BigDecimal.ZERO),
                        mapaAtual.getOrDefault(cat, BigDecimal.ZERO)))
                .toList();
    }
}
