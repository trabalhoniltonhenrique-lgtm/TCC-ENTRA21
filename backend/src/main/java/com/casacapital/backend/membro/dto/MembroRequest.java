package com.casacapital.backend.membro.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MembroRequest(
        @NotBlank String nome,
        @NotBlank String parentesco,
        LocalDate nascimento,
        BigDecimal renda,
        String obs,
        String cor
) {
}
