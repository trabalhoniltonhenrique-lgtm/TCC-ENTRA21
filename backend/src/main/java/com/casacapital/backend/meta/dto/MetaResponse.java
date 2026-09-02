package com.casacapital.backend.meta.dto;

import com.casacapital.backend.meta.Meta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MetaResponse(
        Long id,
        String nome,
        BigDecimal valorAlvo,
        LocalDate prazo,
        boolean concluida,
        BigDecimal totalPoupado,
        List<AporteResponse> aportes
) {
    public static MetaResponse from(Meta m) {
        return new MetaResponse(m.getId(), m.getNome(), m.getValorAlvo(), m.getPrazo(), m.isConcluida(),
                m.totalPoupado(), m.getAportes().stream().map(AporteResponse::from).toList());
    }
}
