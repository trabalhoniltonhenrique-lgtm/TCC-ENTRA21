package com.casacapital.backend.orcamento;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.orcamento.dto.OrcamentoItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Orçamento do mês: só categorias de despesa e meses que existem. */
class OrcamentoMensalServiceTest {

    private static final long FAMILIA = 1L;

    private OrcamentoMensalRepository repo;
    private OrcamentoMensalService service;

    @BeforeEach
    void setUp() {
        repo = mock(OrcamentoMensalRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(FAMILIA)).thenReturn(new Familia());
        when(repo.findByFamiliaIdAndMesAnoAndCategoria(eq(FAMILIA), anyString(), anyString())).thenReturn(Optional.empty());
        when(repo.save(any(OrcamentoMensal.class))).thenAnswer(i -> i.getArgument(0));
        service = new OrcamentoMensalService(repo, familias);
    }

    @Test
    void defineOLimiteDaCategoriaNoMes() {
        OrcamentoMensal o = service.definir(FAMILIA, "2026-10", new OrcamentoItemRequest("Alimentação", new BigDecimal("900")));
        assertThat(o.getMesAno()).isEqualTo("2026-10");
        assertThat(o.getLimite()).isEqualByComparingTo("900");
    }

    @Test
    void categoriaQueNaoExisteEhRecusada() {
        assertThatThrownBy(() -> service.definir(FAMILIA, "2026-10", new OrcamentoItemRequest("Nada", BigDecimal.TEN)))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("CATEGORIA_INVALIDA");
        verify(repo, never()).save(any());
    }

    @Test
    void mesQueNaoExisteEhRecusado() {
        for (String mes : List.of("2026-13", "2026-00", "abc", "2026-1")) {
            assertThatThrownBy(() -> service.definir(FAMILIA, mes, new OrcamentoItemRequest("Lazer", BigDecimal.TEN)))
                    .as(mes)
                    .isInstanceOf(ApiException.class)
                    .extracting("code").isEqualTo("MES_ANO_INVALIDO");
        }
        // antes, "2026-13" passava na validação e derrubava o "copiar mês anterior" com erro interno
        assertThatThrownBy(() -> service.copiarMesAnterior(FAMILIA, "2026-13")).isInstanceOf(ApiException.class);
        verify(repo, never()).save(any());
    }
}
