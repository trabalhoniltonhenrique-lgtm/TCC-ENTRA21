package com.casacapital.backend.impostorenda;

import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.investimento.Investimento;
import com.casacapital.backend.investimento.InvestimentoRepository;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.receita.Receita;
import com.casacapital.backend.receita.ReceitaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImpostoRendaServiceTest {

    @Test
    void separaRendimentosDedutiveisEBensDoAno() {
        ReceitaRepository rr = mock(ReceitaRepository.class);
        DespesaRepository dr = mock(DespesaRepository.class);
        InvestimentoRepository ir = mock(InvestimentoRepository.class);
        MembroFamilia ana = new MembroFamilia();
        ana.setNome("Ana");

        when(rr.findAllByFamiliaIdOrderByDataDesc(1L)).thenReturn(List.of(
                rec("Salário", 6000, "2025-03-05", ana), rec("Salário", 6000, "2025-04-05", ana),
                rec("Freelance", 1500, "2025-06-10", null), rec("Salário", 7000, "2026-01-05", ana)));
        when(dr.findAllByFamiliaIdOrderByDataDesc(1L)).thenReturn(List.of(
                desp("Unimed mensalidade", "Saúde", 800, "2025-02-10", ana),
                desp("RAIA DROGASIL SA", "Saúde", 120, "2025-03-01", ana),
                desp("Dra. Paula dentista", "Saúde", 300, "2025-05-01", null),
                desp("PIX Joana", "Saúde", 200, "2025-05-02", null),
                desp("Colégio Sinodal", "Educação", 1200, "2025-02-05", ana),
                desp("Curso de inglês", "Educação", 400, "2025-03-05", ana),
                desp("Mercado", "Alimentação", 900, "2025-03-06", null)));
        Investimento cdb = inv("CDB 110%", "2025-11-10"), novo = inv("Tesouro 2030", "2026-02-01"), semData = inv("Poupança", null);
        when(ir.findAllByFamiliaId(1L)).thenReturn(List.of(cdb, novo, semData));

        ImpostoRendaResponse r = new ImpostoRendaService(rr, dr, ir).gerar(1L, 2025);

        assertEquals(List.of(2026, 2025), r.anosDisponiveis());
        assertEquals(new BigDecimal("13500.00"), r.rendimentos().total()); // a receita de 2026 fica de fora
        assertEquals("Salário", r.rendimentos().porCategoria().get(0).nome());
        assertEquals("Ana", r.rendimentos().porPessoa().get(0).nome());

        assertEquals(new BigDecimal("1420.00"), r.saude().total());
        assertEquals(new BigDecimal("1100.00"), r.saude().totalProvavel()); // plano + dentista
        assertEquals(List.of("PROVAVEL", "NAO", "PROVAVEL", "CONFERIR"),
                r.saude().itens().stream().map(ImpostoRendaResponse.Item::situacao).toList());

        assertEquals(new BigDecimal("1200.00"), r.educacao().totalProvavel()); // colégio sim, curso de inglês não
        assertEquals(List.of("CDB 110%", "Poupança"), r.bens().stream().map(ImpostoRendaResponse.Bem::nome).toList());
    }

    private static Receita rec(String cat, double v, String data, MembroFamilia m) {
        Receita r = new Receita();
        r.setCategoria(cat);
        r.setValor(BigDecimal.valueOf(v).setScale(2));
        r.setData(LocalDate.parse(data));
        r.setMembro(m);
        return r;
    }

    private static Despesa desp(String nome, String cat, double v, String data, MembroFamilia m) {
        Despesa d = new Despesa();
        d.setNome(nome);
        d.setCategoria(cat);
        d.setValor(BigDecimal.valueOf(v).setScale(2));
        d.setData(LocalDate.parse(data));
        d.setMembro(m);
        return d;
    }

    private static Investimento inv(String nome, String aplicacao) {
        Investimento i = new Investimento();
        i.setNome(nome);
        i.setTipo("CDB");
        i.setValorAplicado(new BigDecimal("1000.00"));
        i.setValorAtual(new BigDecimal("1050.00"));
        i.setDataAplicacao(aplicacao == null ? null : LocalDate.parse(aplicacao));
        return i;
    }
}
