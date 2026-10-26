package com.casacapital.backend.fechamento;

import com.casacapital.backend.assinatura.AssinaturaService;
import com.casacapital.backend.assinatura.AssinaturasResponse;
import com.casacapital.backend.conta.Conta;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.investimento.InvestimentoService;
import com.casacapital.backend.meta.Meta;
import com.casacapital.backend.meta.MetaAporte;
import com.casacapital.backend.meta.MetaRepository;
import com.casacapital.backend.orcamento.OrcamentoMensal;
import com.casacapital.backend.orcamento.OrcamentoMensalRepository;
import com.casacapital.backend.planejamento.PatrimonioMensal;
import com.casacapital.backend.planejamento.PatrimonioMensalRepository;
import com.casacapital.backend.receita.Receita;
import com.casacapital.backend.receita.ReceitaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Monta o resumo de um mês a partir do que já está lançado (nada é gravado). */
@Service
public class FechamentoService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ReceitaRepository receitas;
    private final DespesaRepository despesas;
    private final ContaRepository contas;
    private final OrcamentoMensalRepository orcamentos;
    private final MetaRepository metas;
    private final InvestimentoService investimentos;
    private final AssinaturaService assinaturas;
    private final PatrimonioMensalRepository patrimonio;

    public FechamentoService(ReceitaRepository receitas, DespesaRepository despesas, ContaRepository contas,
                             OrcamentoMensalRepository orcamentos, MetaRepository metas, InvestimentoService investimentos,
                             AssinaturaService assinaturas, PatrimonioMensalRepository patrimonio) {
        this.receitas = receitas;
        this.despesas = despesas;
        this.contas = contas;
        this.orcamentos = orcamentos;
        this.metas = metas;
        this.investimentos = investimentos;
        this.assinaturas = assinaturas;
        this.patrimonio = patrimonio;
    }

    @Transactional(readOnly = true)
    public FechamentoResponse gerar(Long familiaId, YearMonth mes) {
        YearMonth anterior = mes.minusMonths(1);
        List<Receita> recs = receitas.findAllByFamiliaIdOrderByDataDesc(familiaId);
        List<Despesa> desps = despesas.findAllByFamiliaIdOrderByDataDesc(familiaId);

        BigDecimal rec = somaReceitas(recs, mes), recAnt = somaReceitas(recs, anterior);
        List<Despesa> doMes = desps.stream().filter(d -> YearMonth.from(d.getData()).equals(mes)).toList();
        BigDecimal desp = soma(doMes.stream().map(Despesa::getValor).toList());
        BigDecimal despAnt = soma(desps.stream().filter(d -> YearMonth.from(d.getData()).equals(anterior)).map(Despesa::getValor).toList());

        // categorias do mês, com a variação em relação ao mês anterior
        Map<String, BigDecimal> porCat = new TreeMap<>(), porCatAnt = new TreeMap<>();
        for (Despesa d : desps) {
            YearMonth m = YearMonth.from(d.getData());
            if (m.equals(mes)) porCat.merge(d.getCategoria(), d.getValor(), BigDecimal::add);
            else if (m.equals(anterior)) porCatAnt.merge(d.getCategoria(), d.getValor(), BigDecimal::add);
        }
        List<FechamentoResponse.Categoria> categorias = new ArrayList<>();
        porCat.forEach((cat, v) -> {
            BigDecimal ant = porCatAnt.getOrDefault(cat, BigDecimal.ZERO);
            categorias.add(new FechamentoResponse.Categoria(cat, v, ant, variacao(v, ant)));
        });
        categorias.sort(Comparator.comparing(FechamentoResponse.Categoria::valor).reversed());

        List<FechamentoResponse.Despesa> maiores = doMes.stream()
                .sorted(Comparator.comparing(Despesa::getValor).reversed()).limit(5)
                .map(d -> new FechamentoResponse.Despesa(d.getData(), d.getNome(), d.getCategoria(), d.getValor())).toList();

        List<FechamentoResponse.Orcamento> orc = new ArrayList<>();
        for (OrcamentoMensal o : orcamentos.findAllByFamiliaIdAndMesAno(familiaId, mes.toString())) {
            BigDecimal gasto = porCat.getOrDefault(o.getCategoria(), BigDecimal.ZERO);
            orc.add(new FechamentoResponse.Orcamento(o.getCategoria(), o.getLimite(), gasto, gasto.compareTo(o.getLimite()) > 0));
        }

        Map<Long, BigDecimal> investido = investimentos.totalPorMeta(familiaId);
        List<FechamentoResponse.Meta> mts = new ArrayList<>();
        for (Meta m : metas.findAllByFamiliaIdFetchAportes(familiaId)) {
            BigDecimal noMes = soma(m.getAportes().stream().filter(a -> YearMonth.from(a.getData()).equals(mes))
                    .map(MetaAporte::getValor).toList());
            BigDecimal total = m.totalPoupado().add(investido.getOrDefault(m.getId(), BigDecimal.ZERO));
            BigDecimal pct = m.getValorAlvo().signum() > 0
                    ? total.multiply(CEM).divide(m.getValorAlvo(), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            mts.add(new FechamentoResponse.Meta(m.getNome(), noMes, total, m.getValorAlvo(), pct));
        }

        // assinaturas cobradas no mês
        AssinaturasResponse assin = assinaturas.listar(familiaId, mes.atEndOfMonth());
        List<FechamentoResponse.Assinatura> cobradas = assin.assinaturas().stream()
                .filter(a -> YearMonth.from(a.ultimaCobranca()).equals(mes))
                .map(a -> new FechamentoResponse.Assinatura(a.nome(), a.valor(), a.aumentoPct())).toList();
        FechamentoResponse.Assinaturas assinaturasDoMes = new FechamentoResponse.Assinaturas(cobradas.size(),
                soma(cobradas.stream().map(FechamentoResponse.Assinatura::valor).toList()), cobradas);

        FechamentoResponse.Patrimonio patr = patrimonio.findByFamiliaIdAndMes(familiaId, mes.toString())
                .map(atual -> {
                    BigDecimal ant = patrimonio.findByFamiliaIdAndMes(familiaId, anterior.toString())
                            .map(PatrimonioMensal::getTotal).orElse(null);
                    return new FechamentoResponse.Patrimonio(atual.getTotal(), ant, ant == null ? null : atual.getTotal().subtract(ant));
                }).orElse(null);

        List<Conta> pagas = contas.findAllByFamiliaIdOrderByVencimentoAsc(familiaId).stream()
                .filter(c -> c.isPago() && c.getDataPagamento() != null && YearMonth.from(c.getDataPagamento()).equals(mes)).toList();

        return new FechamentoResponse(mes.toString(), rec, desp, rec.subtract(desp), recAnt, despAnt, variacao(desp, despAnt),
                categorias, maiores, orc, mts, assinaturasDoMes, patr,
                new FechamentoResponse.ContasPagas(pagas.size(), soma(pagas.stream().map(Conta::getValor).toList())));
    }

    private static BigDecimal somaReceitas(List<Receita> l, YearMonth mes) {
        return soma(l.stream().filter(r -> YearMonth.from(r.getData()).equals(mes)).map(Receita::getValor).toList());
    }

    private static BigDecimal soma(List<BigDecimal> valores) {
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /** % de variação de "atual" sobre "anterior"; null se não havia valor anterior. */
    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior == null || anterior.signum() == 0) return null;
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 1, RoundingMode.HALF_UP);
    }
}
