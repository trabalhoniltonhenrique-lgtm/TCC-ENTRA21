package com.casacapital.backend.analise.dto;

import java.math.BigDecimal;

public record SerieMensalResponse(String mesAno, BigDecimal receitas, BigDecimal despesas, BigDecimal saldo) {
}
