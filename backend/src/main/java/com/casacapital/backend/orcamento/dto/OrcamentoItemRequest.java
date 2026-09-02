package com.casacapital.backend.orcamento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrcamentoItemRequest(@NotBlank String categoria, @NotNull @DecimalMin("0.01") BigDecimal limite) {
}
