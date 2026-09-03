package com.casacapital.backend.analise.dto;

import java.math.BigDecimal;

public record TendenciaCategoriaResponse(String categoria, BigDecimal anterior, BigDecimal atual) {
}
