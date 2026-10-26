package com.casacapital.backend.investimento.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/** CDI em % ao ano (ex.: 14.90). Null = apaga o valor. */
public record CdiRequest(@DecimalMin("0.00") @DecimalMax("100.00") BigDecimal cdiAnual) {
}
