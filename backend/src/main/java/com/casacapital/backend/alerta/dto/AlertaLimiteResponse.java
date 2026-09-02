package com.casacapital.backend.alerta.dto;

import com.casacapital.backend.alerta.AlertaLimite;

import java.math.BigDecimal;

public record AlertaLimiteResponse(Long id, String categoria, BigDecimal limite) {
    public static AlertaLimiteResponse from(AlertaLimite a) {
        return new AlertaLimiteResponse(a.getId(), a.getCategoria(), a.getLimite());
    }
}
