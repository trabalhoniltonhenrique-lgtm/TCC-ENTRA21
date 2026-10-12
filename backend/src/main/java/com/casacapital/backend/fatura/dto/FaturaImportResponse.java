package com.casacapital.backend.fatura.dto;

import java.math.BigDecimal;

public record FaturaImportResponse(
        int despesasCriadas,
        int parcelamentosCriados,
        int parcelasQuitadas,
        BigDecimal totalImportado
) {
}
