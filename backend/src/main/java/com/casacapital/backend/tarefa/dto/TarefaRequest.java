package com.casacapital.backend.tarefa.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record TarefaRequest(@NotBlank String nome, LocalDate prazo, Long membroId) {
}
