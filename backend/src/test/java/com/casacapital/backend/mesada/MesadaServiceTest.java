package com.casacapital.backend.mesada;

import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MesadaServiceTest {

    private final List<MesadaMovimento> lancados = new ArrayList<>();
    private MesadaService service;
    private Mesada mesada;

    @BeforeEach
    void setUp() {
        MesadaMovimentoRepository movs = mock(MesadaMovimentoRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        Familia familia = new Familia();
        familia.setId(1L);
        when(familias.getReferenceById(anyLong())).thenReturn(familia);
        when(movs.existsByMembroIdAndReferencia(anyLong(), anyString()))
                .thenAnswer(i -> lancados.stream().anyMatch(m -> i.getArgument(1).equals(m.getReferencia())));
        when(movs.saveAndFlush(any())).thenAnswer(i -> {
            lancados.add(i.getArgument(0));
            return i.getArgument(0);
        });
        service = new MesadaService(mock(MesadaRepository.class), movs, mock(MembroFamiliaRepository.class), familias,
                mock(UsuarioRepository.class), mock(PasswordEncoder.class));

        MembroFamilia joao = new MembroFamilia();
        joao.setId(20L);
        mesada = new Mesada();
        mesada.setFamilia(familia);
        mesada.setMembro(joao);
        mesada.setValor(new BigDecimal("50.00"));
        mesada.setDiaPagamento(31);
        mesada.setInicio(LocalDate.of(2026, 1, 10));
    }

    @Test
    void creditaUmaVezPorMesNoDiaEscolhidoOuNoUltimoDia() {
        service.garantirCreditos(mesada, LocalDate.of(2026, 3, 15));
        // janeiro no dia 31 e fevereiro no último dia (28); março ainda não chegou
        assertEquals(List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)),
                lancados.stream().map(MesadaMovimento::getData).toList());
        assertEquals("Mesada de fevereiro/2026", lancados.get(1).getDescricao());

        service.garantirCreditos(mesada, LocalDate.of(2026, 3, 31));
        assertEquals(3, lancados.size()); // não repete janeiro e fevereiro
        assertEquals(TipoMovimentoMesada.MESADA, lancados.get(2).getTipo());
    }

    @Test
    void naoCreditaMesDeInicioSeODiaJaPassouNemMesadaParada() {
        mesada.setDiaPagamento(5);
        service.garantirCreditos(mesada, LocalDate.of(2026, 2, 4));
        assertEquals(0, lancados.size()); // começou em 10/jan, depois do dia 5; fevereiro ainda no dia 4

        mesada.setAtiva(false);
        service.garantirCreditos(mesada, LocalDate.of(2026, 6, 1));
        assertEquals(0, lancados.size());
    }

    @Test
    void proximoPagamento() {
        mesada.setDiaPagamento(10);
        assertEquals(LocalDate.of(2026, 3, 10), MesadaService.proximoPagamento(mesada, LocalDate.of(2026, 3, 9)));
        assertEquals(LocalDate.of(2026, 4, 10), MesadaService.proximoPagamento(mesada, LocalDate.of(2026, 3, 10)));
        assertEquals(LocalDate.of(2026, 2, 28), MesadaService.diaDoPagamento(YearMonth.of(2026, 2), 30));
    }
}
