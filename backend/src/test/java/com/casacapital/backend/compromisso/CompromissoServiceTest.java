package com.casacapital.backend.compromisso;

import com.casacapital.backend.compromisso.dto.CompromissoResponse;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompromissoServiceTest {

    private static final long F = 1L;
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 27, 14, 0);

    private final List<Compromisso> lista = new ArrayList<>();
    private CompromissoService service;
    private CompromissoRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(CompromissoRepository.class);
        when(repo.findAllByFamiliaIdAndAvisoDispensadoFalseAndInicioBetween(eq(F), any(), any()))
                .thenAnswer(i -> lista.stream().filter(c -> !c.isAvisoDispensado()
                        && !c.getInicio().isBefore(i.getArgument(1)) && !c.getInicio().isAfter(i.getArgument(2))).toList());
        service = new CompromissoService(repo, mock(FamiliaRepository.class), mock(MembroFamiliaRepository.class));
    }

    @Test
    void alertaApareceDaAntecedenciaAteUmaHoraDepois() {
        add(1, "Dentista", AGORA.plusMinutes(15), 15, null);        // chegou a hora do aviso
        add(2, "Reunião da escola", AGORA.plusMinutes(16), 15, null); // ainda falta 1 minuto para avisar
        add(3, "Consulta", AGORA.minusMinutes(59), 15, null);        // começou há 59 min, ninguém viu
        add(4, "Vacina", AGORA.minusMinutes(61), 15, null);          // passou da janela
        add(5, "Viagem", AGORA.plusDays(1), 1440, null);             // aviso um dia antes
        add(6, "Futebol", AGORA.plusMinutes(5), 30, null).setAvisoDispensado(true); // já deram "Ciente"

        assertEquals(List.of("Consulta", "Dentista", "Viagem"), titulos(service.alertas(F, null, AGORA)));
    }

    @Test
    void lembrarDepoisAdiaOAlerta() {
        add(1, "Dentista", AGORA.plusMinutes(10), 15, null).setAdiadoAte(AGORA.plusMinutes(5));
        assertEquals(List.of(), titulos(service.alertas(F, null, AGORA)));
        assertEquals(List.of("Dentista"), titulos(service.alertas(F, null, AGORA.plusMinutes(5))));
    }

    @Test
    void filhoSoRecebeOsAlertasDele() {
        add(1, "Natação do João", AGORA.plusMinutes(5), 15, 20L);
        add(2, "Médico da Maria", AGORA.plusMinutes(5), 15, 21L);
        add(3, "Reunião dos pais", AGORA.plusMinutes(5), 15, null);

        assertEquals(List.of("Natação do João"), titulos(service.alertas(F, 20L, AGORA)));
        assertEquals(3, service.alertas(F, null, AGORA).size()); // os pais recebem todos
    }

    @Test
    void lembrarDepoisFicaEntreUmMinutoEUmDia() {
        Compromisso c = add(1, "Dentista", AGORA.plusHours(2), 15, null);
        when(repo.findByIdAndFamiliaId(1L, F)).thenReturn(java.util.Optional.of(c));

        service.adiar(F, 1L, -5, null);      // minutos negativos viram 1 minuto
        assertTrue(c.getAdiadoAte().isBefore(LocalDateTime.now().plusMinutes(2)));
        service.adiar(F, 1L, 100000, null);  // mais de um dia vira 1 dia
        assertTrue(c.getAdiadoAte().isBefore(LocalDateTime.now().plusMinutes(1441)));
        assertTrue(c.getAdiadoAte().isAfter(LocalDateTime.now().plusMinutes(1438)));
    }

    @Test
    void naoMexeEmCompromissoDeOutraFamiliaNemDeOutroFilho() {
        Compromisso daIrma = add(2, "Médico da Maria", AGORA.plusMinutes(5), 15, 21L);
        when(repo.findByIdAndFamiliaId(2L, F)).thenReturn(java.util.Optional.of(daIrma));
        when(repo.findByIdAndFamiliaId(2L, 99L)).thenReturn(java.util.Optional.empty());

        assertThrows(com.casacapital.backend.common.ResourceNotFoundException.class, () -> service.adiar(99L, 2L, 5, null));
        assertThrows(com.casacapital.backend.common.ResourceNotFoundException.class, () -> service.adiar(F, 2L, 5, 20L));
        assertEquals(null, daIrma.getAdiadoAte());
    }

    private Compromisso add(long id, String titulo, LocalDateTime inicio, int antecedencia, Long membroId) {
        Compromisso c = new Compromisso();
        c.setId(id);
        c.setTitulo(titulo);
        c.setInicio(inicio);
        c.setAntecedenciaMin(antecedencia);
        if (membroId != null) {
            MembroFamilia m = new MembroFamilia();
            m.setId(membroId);
            m.setNome("Membro " + membroId);
            c.setMembro(m);
        }
        lista.add(c);
        return c;
    }

    private static List<String> titulos(List<CompromissoResponse> r) {
        return r.stream().map(CompromissoResponse::titulo).toList();
    }
}
