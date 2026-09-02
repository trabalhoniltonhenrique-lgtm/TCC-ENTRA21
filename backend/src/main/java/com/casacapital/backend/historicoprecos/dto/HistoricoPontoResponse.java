package com.casacapital.backend.historicoprecos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricoPontoResponse(LocalDate data, BigDecimal preco) {
}
