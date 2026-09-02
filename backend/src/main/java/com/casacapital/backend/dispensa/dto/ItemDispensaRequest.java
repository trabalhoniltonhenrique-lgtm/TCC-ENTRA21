package com.casacapital.backend.dispensa.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ItemDispensaRequest(
        @NotBlank String nome,
        @Min(0) Integer qtd,
        @NotBlank String categoria
) {
}
