package com.casacapital.backend.tarefa.dto;

import com.casacapital.backend.tarefa.Tarefa;

import java.time.LocalDate;

public record TarefaResponse(Long id, String nome, LocalDate prazo, boolean concluida, Long membroId) {
    public static TarefaResponse from(Tarefa t) {
        return new TarefaResponse(t.getId(), t.getNome(), t.getPrazo(), t.isConcluida(),
                t.getMembro() != null ? t.getMembro().getId() : null);
    }
}
