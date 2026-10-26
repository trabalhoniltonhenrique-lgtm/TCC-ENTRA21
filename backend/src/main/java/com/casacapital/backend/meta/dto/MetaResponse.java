package com.casacapital.backend.meta.dto;

import com.casacapital.backend.meta.Meta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * totalPoupado = aportes registrados + valor atual dos investimentos ligados à meta.
 * concluida: marcada ao atingir o alvo por aportes, ou quando o total (com investimentos) chega ao alvo.
 */
public record MetaResponse(
        Long id,
        String nome,
        BigDecimal valorAlvo,
        LocalDate prazo,
        boolean concluida,
        BigDecimal totalPoupado,
        BigDecimal totalAportes,
        BigDecimal totalInvestimentos,
        List<AporteResponse> aportes
) {
    public static MetaResponse from(Meta m, BigDecimal totalInvestimentos) {
        BigDecimal investido = totalInvestimentos != null ? totalInvestimentos : BigDecimal.ZERO;
        BigDecimal aportes = m.totalPoupado();
        BigDecimal total = aportes.add(investido);
        return new MetaResponse(m.getId(), m.getNome(), m.getValorAlvo(), m.getPrazo(),
                m.isConcluida() || total.compareTo(m.getValorAlvo()) >= 0, total, aportes, investido,
                m.getAportes().stream().map(AporteResponse::from).toList());
    }
}
