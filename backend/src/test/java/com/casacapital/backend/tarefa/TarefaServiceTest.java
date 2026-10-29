package com.casacapital.backend.tarefa;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamilia;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.tarefa.dto.TarefaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tarefas: concluir e desfazer, responsável só da própria família, editar sem responsável. */
class TarefaServiceTest {

    private static final long FAMILIA = 1L, OUTRA = 2L;

    private TarefaRepository tarefas;
    private TarefaService service;
    private final MembroFamilia filho = new MembroFamilia();

    @BeforeEach
    void setUp() {
        tarefas = mock(TarefaRepository.class);
        MembroFamiliaRepository membros = mock(MembroFamiliaRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(any())).thenReturn(new Familia());
        when(tarefas.save(any(Tarefa.class))).thenAnswer(i -> i.getArgument(0));
        filho.setId(20L);
        when(membros.findByIdAndFamiliaId(20L, FAMILIA)).thenReturn(Optional.of(filho));
        when(membros.findByIdAndFamiliaId(20L, OUTRA)).thenReturn(Optional.empty());
        service = new TarefaService(tarefas, familias, membros);
    }

    @Test
    void criaTarefaDoDiaDaSemanaComResponsavel() {
        Tarefa t = service.criar(FAMILIA, new TarefaRequest("Arrumar o quarto", null, 20L, 6));
        assertThat(t.getMembro()).isSameAs(filho);
        assertThat(t.getDiaSemana()).isEqualTo(6);
        assertThat(t.isConcluida()).isFalse();
    }

    @Test
    void concluirDeNovoDesfaz() {
        Tarefa t = new Tarefa();
        when(tarefas.findByIdAndFamiliaId(5L, FAMILIA)).thenReturn(Optional.of(t));

        service.concluir(FAMILIA, 5L);
        assertThat(t.isConcluida()).isTrue();
        service.concluir(FAMILIA, 5L);
        assertThat(t.isConcluida()).isFalse();
    }

    @Test
    void responsavelDeOutraFamiliaEhRecusado() {
        assertThatThrownBy(() -> service.criar(OUTRA, new TarefaRequest("X", null, 20L, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tarefas, never()).save(any());
    }

    @Test
    void editarSemResponsavelTiraOResponsavel() {
        Tarefa t = new Tarefa();
        t.setMembro(filho);
        when(tarefas.findByIdAndFamiliaId(5L, FAMILIA)).thenReturn(Optional.of(t));

        service.atualizar(FAMILIA, 5L, new TarefaRequest("Pagar IPTU", LocalDate.of(2026, 11, 20), null, null));

        assertThat(t.getMembro()).isNull();
        assertThat(t.getPrazo()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void naoMexeEmTarefaDeOutraFamilia() {
        when(tarefas.findByIdAndFamiliaId(5L, OUTRA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.concluir(OUTRA, 5L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.excluir(OUTRA, 5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(tarefas, never()).delete(any());
    }
}
