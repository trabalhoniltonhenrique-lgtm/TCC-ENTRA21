package com.casacapital.backend.mesada.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MesadaConfigRequest(
        @NotNull @DecimalMin(value = "0.01", message = "informe o valor da mesada") BigDecimal valor,
        @NotNull @Min(1) @Max(31) Integer diaPagamento,
        Boolean ativa,
        @Size(max = 120) String metaNome,
        @DecimalMin(value = "0.01", message = "o objetivo precisa ser maior que zero") BigDecimal metaValor
) {
}
