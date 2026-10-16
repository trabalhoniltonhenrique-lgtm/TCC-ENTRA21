package com.casacapital.backend.fatura.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Conta de luz/água revisada pelo usuário. contaId != null: atualiza essa conta em aberto em vez de criar outra. */
public record ContaConsumoImportRequest(
        @NotBlank String descricao,
        @NotBlank String categoria,
        @NotNull @DecimalMin(value = "0.01") BigDecimal valor,
        @NotNull LocalDate vencimento,
        Long contaId
) {
}
