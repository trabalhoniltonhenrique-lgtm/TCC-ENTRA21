package com.casacapital.backend.investimento.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestimentoRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank String tipo,
        @Size(max = 60) String instituicao,
        @NotNull @DecimalMin("0.00") BigDecimal valorAplicado,
        @NotNull @DecimalMin("0.00") BigDecimal valorAtual,
        // Rentabilidade para projeção: taxa + tipoTaxa ("MES" = % ao mês, "ANO" = % ao ano, "CDI" = % do CDI)
        @DecimalMin("0.00") @DecimalMax("1000.00") BigDecimal taxa,
        String tipoTaxa,
        LocalDate dataAplicacao,
        LocalDate vencimento,
        @Size(max = 255) String obs,
        Long metaId
) {
}
