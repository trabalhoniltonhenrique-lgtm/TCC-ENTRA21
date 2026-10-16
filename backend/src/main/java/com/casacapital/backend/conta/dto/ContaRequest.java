package com.casacapital.backend.conta.dto;

import com.casacapital.backend.conta.Recorrencia;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaRequest(
        @NotBlank String descricao,
        @NotBlank String categoria,
        @NotNull @DecimalMin(value = "0.01") BigDecimal valor,
        @NotNull LocalDate vencimento,
        @NotNull Recorrencia recorrencia,
        String obs,
        // Cartão/banco opcional (ex.: "Itaú"); vazio = conta sem cartão
        @Size(max = 40) String cartao,
        // Usados apenas quando recorrencia == PARCELADA, na criação:
        Integer qtdParcelas,
        // "total" -> valor informado é o total (divide por qtdParcelas); qualquer outro valor -> valor é por parcela
        String tipoValorParcela
) {
}
