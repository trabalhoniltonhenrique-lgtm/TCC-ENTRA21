package com.casacapital.backend.meta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AporteRequest(@NotNull @DecimalMin("0.01") BigDecimal valor) {
}
