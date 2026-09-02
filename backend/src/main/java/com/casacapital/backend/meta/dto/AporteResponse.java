package com.casacapital.backend.meta.dto;

import com.casacapital.backend.meta.MetaAporte;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AporteResponse(Long id, BigDecimal valor, LocalDate data) {
    public static AporteResponse from(MetaAporte a) {
        return new AporteResponse(a.getId(), a.getValor(), a.getData());
    }
}
