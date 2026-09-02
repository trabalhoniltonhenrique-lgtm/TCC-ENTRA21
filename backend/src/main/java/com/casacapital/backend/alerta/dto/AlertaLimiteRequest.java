package com.casacapital.backend.alerta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AlertaLimiteRequest(@NotBlank String categoria, @NotNull @DecimalMin("0.01") BigDecimal limite) {
}
