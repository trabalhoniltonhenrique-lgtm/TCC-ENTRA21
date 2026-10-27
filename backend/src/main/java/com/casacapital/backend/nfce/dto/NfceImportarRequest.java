package com.casacapital.backend.nfce.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** O que a família confirmou na revisão da nota. */
public record NfceImportarRequest(
        @NotNull @Pattern(regexp = "\\d{44}", message = "chave de acesso inválida") String chave,
        @Size(max = 160) String emitente,
        @Size(max = 18) String cnpj,
        LocalDate dataEmissao,
        @NotNull @DecimalMin("0.01") BigDecimal total,
        @NotEmpty @Size(max = 300) List<@Valid Item> itens,
        boolean lancarDespesa,
        String categoriaDespesa,
        Long membroId
) {
    public record Item(
            @NotBlank @Size(max = 160) String nome,
            @Size(max = 60) String codigo,
            @NotNull @DecimalMin("0.01") BigDecimal valorUnitario,
            boolean registrarPreco,
            boolean despensa,
            @Min(1) @Max(999) Integer qtdDespensa,
            String categoria
    ) {
    }
}
