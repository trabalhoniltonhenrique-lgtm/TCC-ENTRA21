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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FechamentoServiceTest {

    private static final long F = 1L;
    private static final YearMonth SET = YearMonth.of(2026, 9);

    @Test
    void montaOResumoDoMesComparandoComOAnterior() {
        ReceitaRepository rr = mock(ReceitaRepository.class);
        DespesaRepository dr = mock(DespesaRepository.class);
        ContaRepository cr = mock(ContaRepository.class);
        OrcamentoMensalRepository or = mock(OrcamentoMensalRepository.class);
        MetaRepository mr = mock(MetaRepository.class);
        InvestimentoService is = mock(InvestimentoService.class);
        AssinaturaService as = mock(AssinaturaService.class);
        PatrimonioMensalRepository pr = mock(PatrimonioMensalRepository.class);

        when(rr.findAllByFamiliaIdOrderByDataDesc(F)).thenReturn(List.of(rec(5000, "2026-09-05"), rec(4000, "2026-08-05")));
        when(dr.findAllByFamiliaIdOrderByDataDesc(F)).thenReturn(List.of(
                desp("Mercado", "Alimentação", 1500, "2026-09-10"), desp("Uber", "Transporte", 400, "2026-09-12"),
                desp("Mercado", "Alimentação", 1000, "2026-08-10"), desp("Uber", "Transporte", 500, "2026-08-12")));
        Conta luz = new Conta();
        luz.setValor(new BigDecimal("140.36"));
        luz.setPago(true);
        luz.setDataPagamento(LocalDate.of(2026, 9, 20));
        when(cr.findAllByFamiliaIdOrderByVencimentoAsc(F)).thenReturn(List.of(luz));
        OrcamentoMensal limiteAlim = new OrcamentoMensal();
        limiteAlim.setCategoria("Alimentação");
        limiteAlim.setLimite(new BigDecimal("1200.00"));
        when(or.findAllByFamiliaIdAndMesAno(F, "2026-09")).thenReturn(List.of(limiteAlim));
        Meta viagem = new Meta();
        viagem.setId(3L);
        viagem.setNome("Viagem");
        viagem.setValorAlvo(new BigDecimal("8000.00"));
        viagem.getAportes().add(new MetaAporte(viagem, new BigDecimal("500.00"), LocalDate.of(2026, 9, 1)));
        viagem.getAportes().add(new MetaAporte(viagem, new BigDecimal("1500.00"), LocalDate.of(2026, 7, 1)));
        when(mr.findAllByFamiliaIdFetchAportes(F)).thenReturn(List.of(viagem));
        when(is.totalPorMeta(F)).thenReturn(Map.of(3L, new BigDecimal("2000.00")));
        when(as.listar(any(), any())).thenReturn(new AssinaturasResponse(BigDecimal.ZERO, BigDecimal.ZERO, List.of(
                new AssinaturasResponse.Assinatura("NETFLIX COM", "NETFLIX.COM", "Lazer", new BigDecimal("44.90"), new BigDecimal("39.90"),
                        new BigDecimal("12.5"), LocalDate.of(2026, 6, 10), LocalDate.of(2026, 9, 10), 4, true, false, false),
                new AssinaturasResponse.Assinatura("DISNEY PLUS", "DISNEY", "Lazer", new BigDecimal("33.90"), null, null,
                        LocalDate.of(2026, 5, 8), LocalDate.of(2026, 7, 8), 3, false, false, false)), List.of()));
        PatrimonioMensal pSet = new PatrimonioMensal(), pAgo = new PatrimonioMensal();
        pSet.setTotal(new BigDecimal("15620.00"));
        pAgo.setTotal(new BigDecimal("14100.00"));
        when(pr.findByFamiliaIdAndMes(F, "2026-09")).thenReturn(Optional.of(pSet));
        when(pr.findByFamiliaIdAndMes(F, "2026-08")).thenReturn(Optional.of(pAgo));

        FechamentoResponse r = new FechamentoService(rr, dr, cr, or, mr, is, as, pr).gerar(F, SET);

        assertEquals(new BigDecimal("5000.00"), r.receitas());
        assertEquals(new BigDecimal("1900.00"), r.despesas());
        assertEquals(new BigDecimal("3100.00"), r.saldo());
        assertEquals(new BigDecimal("26.7"), r.variacaoDespesasPct()); // 1.500 -> 1.900

        assertEquals("Alimentação", r.categorias().get(0).categoria());
        assertEquals(new BigDecimal("50.0"), r.categorias().get(0).variacaoPct()); // 1.000 -> 1.500
        assertEquals(new BigDecimal("-20.0"), r.categorias().get(1).variacaoPct()); // 500 -> 400

        assertTrue(r.orcamento().get(0).estourou()); // 1.500 de 1.200

        FechamentoResponse.Meta m = r.metas().get(0);
        assertEquals(new BigDecimal("500.00"), m.aportadoNoMes());
        assertEquals(new BigDecimal("4000.00"), m.totalPoupado()); // 2.000 aportes + 2.000 investimentos
        assertEquals(new BigDecimal("50"), m.pct());

        assertEquals(1, r.assinaturas().quantidade()); // só a cobrada em setembro
        assertEquals(new BigDecimal("1520.00"), r.patrimonio().variacao());
        assertEquals(1, r.contasPagas().quantidade());

        String email = new FechamentoEmailService(null, null, null, null, null, "", "http://localhost:8080").texto("Família Teste", r, SET);
        assertTrue(email.contains("Setembro de 2026"));
        assertTrue(email.contains("Despesas:  R$ 1.900,00  (+26,7% vs. mês anterior)"));
        assertTrue(email.contains("Passou do limite: Alimentação"));
        assertTrue(email.contains("NETFLIX.COM aumentou 12,5%"));
        assertTrue(email.contains("+R$ 1.520,00 no mês"));
    }

    @Test
    void semMesAnteriorNaoInventaVariacao() {
        assertNull(FechamentoService.variacao(new BigDecimal("100"), BigDecimal.ZERO));
        assertFalse(FechamentoEmailService.dinheiro(new BigDecimal("-5")).startsWith("R$"));
    }

    private static Receita rec(double v, String data) {
        Receita r = new Receita();
        r.setValor(BigDecimal.valueOf(v).setScale(2));
        r.setData(LocalDate.parse(data));
        return r;
    }

    private static Despesa desp(String nome, String cat, double v, String data) {
        Despesa d = new Despesa();
        d.setNome(nome);
        d.setCategoria(cat);
        d.setValor(BigDecimal.valueOf(v).setScale(2));
        d.setData(LocalDate.parse(data));
        return d;
    }
}
