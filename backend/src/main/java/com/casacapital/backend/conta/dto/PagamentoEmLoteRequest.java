package com.casacapital.backend.conta.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PagamentoEmLoteRequest(
        @NotEmpty @Size(max = 500) List<@NotNull Long> ids
) {
}
