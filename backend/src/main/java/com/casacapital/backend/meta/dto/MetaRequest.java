package com.casacapital.backend.meta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MetaRequest(@NotBlank String nome, @NotNull @DecimalMin("0.01") BigDecimal valorAlvo, LocalDate prazo) {
}
