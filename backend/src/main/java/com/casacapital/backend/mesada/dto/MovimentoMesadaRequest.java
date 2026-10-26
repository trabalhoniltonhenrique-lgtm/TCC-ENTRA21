package com.casacapital.backend.mesada.dto;

import com.casacapital.backend.mesada.TipoMovimentoMesada;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lançamento manual: EXTRA (dinheiro a mais) ou GASTO. Sem data, vale hoje. */
public record MovimentoMesadaRequest(
        @NotNull TipoMovimentoMesada tipo,
        @NotNull @DecimalMin(value = "0.01", message = "informe o valor") BigDecimal valor,
        @NotBlank @Size(max = 160) String descricao,
        LocalDate data
) {
}
