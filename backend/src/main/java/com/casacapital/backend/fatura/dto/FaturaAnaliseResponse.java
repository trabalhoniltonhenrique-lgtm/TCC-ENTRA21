package com.casacapital.backend.fatura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FaturaAnaliseResponse(
        String emissor,
        LocalDate vencimento,
        BigDecimal total,
        List<ItemFatura> itens,
        List<ItemFatura> creditos
) {
}
