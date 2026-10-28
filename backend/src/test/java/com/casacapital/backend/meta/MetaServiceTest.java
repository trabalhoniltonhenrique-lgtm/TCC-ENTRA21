package com.casacapital.backend.meta;

import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.meta.dto.MetaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Metas: aportes somam, a meta conclui ao atingir o alvo e reabre se o alvo subir. */
class MetaServiceTest {

    private static final long FAMILIA = 1L;

    private MetaService service;
    private Meta viagem;

    @BeforeEach
    void setUp() {
        MetaRepository metas = mock(MetaRepository.class);
        service = new MetaService(metas, mock(FamiliaRepository.class));
        viagem = new Meta();
        viagem.setId(3L);
        viagem.setNome("Viagem");
        viagem.setValorAlvo(new BigDecimal("1000.00"));
        when(metas.findByIdAndFamiliaIdFetchAportes(3L, FAMILIA)).thenReturn(Optional.of(viagem));
    }

    @Test
    void aportesSomamEConcluemAoAtingirOAlvo() {
        service.aportar(FAMILIA, 3L, new BigDecimal("600.00"));
        assertThat(viagem.isConcluida()).isFalse();

        service.aportar(FAMILIA, 3L, new BigDecimal("400.00"));
        assertThat(viagem.totalPoupado()).isEqualByComparingTo("1000.00");
        assertThat(viagem.isConcluida()).isTrue();
    }

    @Test
    void aumentarOAlvoDeUmaMetaConcluidaReabreAMeta() {
        service.aportar(FAMILIA, 3L, new BigDecimal("1000.00"));
        assertThat(viagem.isConcluida()).isTrue();

        service.atualizar(FAMILIA, 3L, new MetaRequest("Viagem", new BigDecimal("1500.00"), null));

        assertThat(viagem.isConcluida()).isFalse();
    }

    @Test
    void diminuirOAlvoParaAbaixoDoPoupadoConcluiAMeta() {
        service.aportar(FAMILIA, 3L, new BigDecimal("700.00"));

        service.atualizar(FAMILIA, 3L, new MetaRequest("Viagem", new BigDecimal("500.00"), null));

        assertThat(viagem.isConcluida()).isTrue();
    }
}
