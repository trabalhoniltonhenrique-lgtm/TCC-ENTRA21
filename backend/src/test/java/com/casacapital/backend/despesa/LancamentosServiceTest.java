package com.casacapital.backend.despesa;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.despesa.dto.DespesaRequest;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.receita.Receita;
import com.casacapital.backend.receita.ReceitaRepository;
import com.casacapital.backend.receita.ReceitaService;
import com.casacapital.backend.receita.dto.ReceitaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Despesas e receitas: categoria válida, membro só da própria família, nada de outra família. */
class LancamentosServiceTest {

    private static final long FAMILIA = 1L, OUTRA_FAMILIA = 2L;
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 28);

    private DespesaRepository despesas;
    private ReceitaRepository receitas;
    private MembroFamiliaRepository membros;
    private DespesaService despesaService;
    private ReceitaService receitaService;
    private final MembroFamilia filho = new MembroFamilia();

    @BeforeEach
    void setUp() {
        despesas = mock(DespesaRepository.class);
        receitas = mock(ReceitaRepository.class);
        membros = mock(MembroFamiliaRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(FAMILIA)).thenReturn(new Familia());
        when(despesas.save(any(Despesa.class))).thenAnswer(i -> i.getArgument(0));
        when(receitas.save(any(Receita.class))).thenAnswer(i -> i.getArgument(0));
        filho.setId(20L);
        when(membros.findByIdAndFamiliaId(20L, FAMILIA)).thenReturn(Optional.of(filho));
        when(membros.findByIdAndFamiliaId(20L, OUTRA_FAMILIA)).thenReturn(Optional.empty());
        despesaService = new DespesaService(despesas, familias, membros);
        receitaService = new ReceitaService(receitas, familias, membros);
    }

    @Test
    void despesaComMembroDaFamilia() {
        Despesa d = despesaService.criar(FAMILIA, new DespesaRequest("Mercado", new BigDecimal("312.45"),
                "Alimentação", HOJE, 20L));

        assertThat(d.getMembro()).isSameAs(filho);
        assertThat(d.getValor()).isEqualByComparingTo("312.45");
        assertThat(d.getData()).isEqualTo(HOJE);
    }

    @Test
    void despesaComCategoriaInexistenteEhRecusada() {
        assertThatThrownBy(() -> despesaService.criar(FAMILIA,
                new DespesaRequest("Mercado", BigDecimal.TEN, "Alimentacao sem acento", HOJE, null)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CATEGORIA_INVALIDA");
        verify(despesas, never()).save(any());
    }

    @Test
    void despesaNaoAceitaMembroDeOutraFamilia() {
        assertThatThrownBy(() -> despesaService.criar(OUTRA_FAMILIA,
                new DespesaRequest("Mercado", BigDecimal.TEN, "Alimentação", HOJE, 20L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(despesas, never()).save(any());
    }

    @Test
    void editarSemMembroTiraOMembro() {
        Despesa d = new Despesa();
        d.setMembro(filho);
        when(despesas.findByIdAndFamiliaId(7L, FAMILIA)).thenReturn(Optional.of(d));

        despesaService.atualizar(FAMILIA, 7L, new DespesaRequest("Farmácia", BigDecimal.TEN, "Saúde", HOJE, null));

        assertThat(d.getMembro()).isNull();
        assertThat(d.getCategoria()).isEqualTo("Saúde");
    }

    @Test
    void naoEditaNemExcluiDespesaDeOutraFamilia() {
        when(despesas.findByIdAndFamiliaId(7L, OUTRA_FAMILIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> despesaService.excluir(OUTRA_FAMILIA, 7L)).isInstanceOf(ResourceNotFoundException.class);
        verify(despesas, never()).delete(any());
    }

    @Test
    void receitaUsaAsCategoriasDeReceita() {
        Receita r = receitaService.criar(FAMILIA, new ReceitaRequest("Salário outubro", new BigDecimal("5200.00"),
                "Salário", HOJE, null));
        assertThat(r.getValor()).isEqualByComparingTo("5200.00");

        // "Alimentação" é categoria de despesa, não de receita
        assertThatThrownBy(() -> receitaService.criar(FAMILIA,
                new ReceitaRequest("Errada", BigDecimal.TEN, "Alimentação", HOJE, null)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CATEGORIA_INVALIDA");
    }

    @Test
    void receitaNaoAceitaMembroDeOutraFamilia() {
        assertThatThrownBy(() -> receitaService.criar(OUTRA_FAMILIA,
                new ReceitaRequest("Mesada", BigDecimal.TEN, "Outros", HOJE, 20L)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(receitas, never()).save(any());
    }
}
