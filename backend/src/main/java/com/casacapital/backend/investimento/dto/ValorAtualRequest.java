package com.casacapital.backend.investimento.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Atualização rápida do valor atual (ex.: conferido no app do banco). */
public record ValorAtualRequest(@NotNull @DecimalMin("0.00") BigDecimal valorAtual) {
}
