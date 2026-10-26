package com.casacapital.backend.planejamento;

import com.casacapital.backend.conta.Conta;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.conta.Recorrencia;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.investimento.Investimento;
import com.casacapital.backend.investimento.InvestimentoRepository;
import com.casacapital.backend.planejamento.dto.FluxoCaixaResponse;
import com.casacapital.backend.planejamento.dto.PatrimonioResponse;
import com.casacapital.backend.receita.Receita;
import com.casacapital.backend.receita.ReceitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanejamentoServiceTest {

    private static final long F = 1L;
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 15);

    private final List<Receita> receitas = new ArrayList<>();
    private final List<Despesa> despesas = new ArrayList<>();
    private final List<Conta> contas = new ArrayList<>();
    private final List<Investimento> invs = new ArrayList<>();
    private PatrimonioMensalRepository fotos;
    private PlanejamentoService service;

    @BeforeEach
    void setUp() {
        ReceitaRepository rr = mock(ReceitaRepository.class);
        DespesaRepository dr = mock(DespesaRepository.class);
        ContaRepository cr = mock(ContaRepository.class);
        InvestimentoRepository ir = mock(InvestimentoRepository.class);
        FamiliaRepository fr = mock(FamiliaRepository.class);
        fotos = mock(PatrimonioMensalRepository.class);
        when(rr.findAllByFamiliaIdOrderByDataDesc(F)).thenReturn(receitas);
        when(dr.findAllByFamiliaIdOrderByDataDesc(F)).thenReturn(despesas);
        when(cr.findAllByFamiliaIdOrderByVencimentoAsc(F)).thenReturn(contas);
        when(ir.findAllByFamiliaId(F)).thenReturn(invs);
        when(fr.getReferenceById(F)).thenReturn(new Familia());
        when(fotos.findByFamiliaIdAndMes(any(), any())).thenReturn(Optional.empty());
        when(fotos.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new PlanejamentoService(rr, dr, cr, ir, fotos, fr);

        // Jul, ago, set: salário 5.000 e 2.000 de gastos do dia a dia por mês; mais 1.200 de aluguel pago (veio de conta)
        for (String mes : List.of("2026-07", "2026-08", "2026-09")) {
            receitas.add(receita(5000, mes + "-05"));
            despesas.add(despesa(2000, mes + "-10", false));
            despesas.add(despesa(1200, mes + "-10", true));
        }
        // Outubro até agora: salário já caiu, 500 gastos
        receitas.add(receita(5000, "2026-10-05"));
        despesas.add(despesa(500, "2026-10-08", false));
    }

    @Test
    void projetaSaldoComMediasContasEmAbertoERecorrencias() {
        contas.add(conta("Aluguel", 1200, "2026-10-20", false, Recorrencia.MENSAL, null)); // mensal em aberto
        contas.add(conta("Geladeira (4/10)", 450, "2026-11-13", false, Recorrencia.PARCELADA, 9L));
        contas.add(conta("Geladeira (5/10)", 450, "2026-12-13", false, Recorrencia.PARCELADA, 9L));
        contas.add(conta("IPVA", 3000, "2026-09-30", false, Recorrencia.UNICA, null)); // atrasada: entra em outubro

        FluxoCaixaResponse r = service.fluxoCaixa(F, 3, HOJE);

        // saldo hoje: 20.000 − (3×3.200 + 500) = 9.900
        assertEquals(new BigDecimal("9900.00"), r.saldoAtual());
        assertEquals(new BigDecimal("5000.00"), r.mediaReceitas());
        assertEquals(new BigDecimal("2000.00"), r.mediaGastosVariaveis()); // o aluguel pago não entra na média

        FluxoCaixaResponse.Mes out = r.meses().get(0);
        assertEquals(new BigDecimal("0.00"), out.receitas()); // o salário de outubro já entrou
        assertEquals(new BigDecimal("4200.00"), out.contas()); // aluguel + IPVA atrasado
        assertEquals(new BigDecimal("1500.00"), out.gastosVariaveis()); // 2.000 − 500 já gastos
        assertEquals(new BigDecimal("4200.00"), out.saldoFinal()); // 9.900 − 4.200 − 1.500

        FluxoCaixaResponse.Mes nov = r.meses().get(1);
        assertEquals(new BigDecimal("1650.00"), nov.contas()); // aluguel previsto + parcela
        assertEquals(new BigDecimal("450.00"), nov.parcelas());
        assertTrue(nov.itens().stream().anyMatch(i -> i.descricao().equals("Aluguel") && i.prevista()));
        assertEquals(new BigDecimal("5550.00"), nov.saldoFinal()); // 4.200 + 5.000 − 1.650 − 2.000

        assertEquals(new BigDecimal("6900.00"), r.meses().get(2).saldoFinal()); // + 5.000 − 1.650 − 2.000
        assertNull(r.primeiroMesNegativo());
    }

    @Test
    void avisaOPrimeiroMesNegativo() {
        contas.add(conta("Viagem (1/3)", 15000, "2026-11-10", false, Recorrencia.PARCELADA, 7L));
        FluxoCaixaResponse r = service.fluxoCaixa(F, 3, HOJE);
        assertEquals("2026-11", r.primeiroMesNegativo()); // 8.400 + 5.000 − 15.000 − 2.000 = −3.600
    }

    @Test
    void patrimonioSomaSaldoEInvestimentosEDescontaParcelas() {
        Investimento cdb = new Investimento();
        cdb.setValorAtual(new BigDecimal("8634.20"));
        invs.add(cdb);
        contas.add(conta("Geladeira (4/10)", 450, "2026-11-13", false, Recorrencia.PARCELADA, 9L));
        contas.add(conta("Geladeira (3/10)", 450, "2026-10-13", true, Recorrencia.PARCELADA, 9L)); // paga: não é dívida
        contas.add(conta("Luz", 140, "2026-10-20", false, Recorrencia.UNICA, null)); // conta comum: não é dívida

        PatrimonioResponse p = service.patrimonio(F, HOJE);

        assertEquals("2026-10", p.atual().mes());
        assertEquals(new BigDecimal("9900.00"), p.atual().saldo());
        assertEquals(new BigDecimal("8634.20"), p.atual().investimentos());
        assertEquals(new BigDecimal("450.00"), p.atual().dividas());
        assertEquals(new BigDecimal("18084.20"), p.atual().total());
    }

    private static Receita receita(double v, String data) {
        Receita r = new Receita();
        r.setValor(BigDecimal.valueOf(v));
        r.setData(LocalDate.parse(data));
        return r;
    }

    private static Despesa despesa(double v, String data, boolean deConta) {
        Despesa d = new Despesa();
        d.setValor(BigDecimal.valueOf(v));
        d.setData(LocalDate.parse(data));
        d.setOrigemContaFlag(deConta);
        return d;
    }

    private static Conta conta(String desc, double v, String venc, boolean pago, Recorrencia rec, Long grupo) {
        Conta c = new Conta();
        c.setDescricao(desc);
        c.setCategoria("Outros");
        c.setValor(BigDecimal.valueOf(v));
        c.setVencimento(LocalDate.parse(venc));
        c.setPago(pago);
        c.setRecorrencia(rec);
        c.setGrupoParcelaId(grupo);
        return c;
    }
}
