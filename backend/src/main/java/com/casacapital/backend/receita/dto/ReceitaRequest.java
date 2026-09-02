package com.casacapital.backend.receita.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceitaRequest(
        @NotBlank String nome,
        @NotNull @DecimalMin(value = "0.01") BigDecimal valor,
        @NotBlank String categoria,
        @NotNull LocalDate data,
        Long membroId
) {
}
