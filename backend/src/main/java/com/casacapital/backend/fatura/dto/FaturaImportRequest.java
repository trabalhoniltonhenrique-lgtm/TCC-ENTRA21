package com.casacapital.backend.fatura.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FaturaImportRequest(
        // Vencimento da fatura: base para as datas das próximas parcelas
        @NotNull LocalDate vencimento,
        // Cartão da fatura (ex.: "Itaú"), gravado nas parcelas criadas/quitadas; opcional
        @Size(max = 40) String cartao,
        @NotEmpty List<@Valid Item> itens
) {
    public record Item(
            @NotNull LocalDate data,
            @NotBlank String descricao,
            @NotNull @DecimalMin(value = "0.01") BigDecimal valor,
            Integer parcelaAtual,
            Integer parcelaTotal,
            @NotBlank String categoria,
            Long contaId
    ) {
    }
}
