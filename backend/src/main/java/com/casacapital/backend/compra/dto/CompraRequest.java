package com.casacapital.backend.compra.dto;

import com.casacapital.backend.compra.Frequencia;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompraRequest(
        @NotBlank String nome,
        @Min(1) Integer qtd,
        @NotBlank String categoria,
        @NotNull Frequencia frequencia
) {
}
