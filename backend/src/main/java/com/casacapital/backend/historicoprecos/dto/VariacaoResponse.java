package com.casacapital.backend.historicoprecos.dto;

import java.math.BigDecimal;

public record VariacaoResponse(BigDecimal atual, BigDecimal anterior, double percentual, boolean subiu, boolean desceu) {
}
