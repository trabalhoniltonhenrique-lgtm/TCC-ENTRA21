package com.casacapital.backend.mesada.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Gasto anotado pelo próprio filho. */
public record GastoMesadaRequest(
        @NotNull @DecimalMin(value = "0.01", message = "informe o valor") BigDecimal valor,
        @NotBlank(message = "diga com o que gastou") @Size(max = 160) String descricao
) {
}
