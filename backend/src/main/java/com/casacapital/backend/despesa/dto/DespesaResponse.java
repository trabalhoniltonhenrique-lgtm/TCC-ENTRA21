package com.casacapital.backend.despesa.dto;

import com.casacapital.backend.despesa.Despesa;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DespesaResponse(
        Long id,
        String nome,
        BigDecimal valor,
        String categoria,
        LocalDate data,
        Long membroId,
        boolean origemConta
) {
    public static DespesaResponse from(Despesa d) {
        return new DespesaResponse(d.getId(), d.getNome(), d.getValor(), d.getCategoria(), d.getData(),
                d.getMembro() != null ? d.getMembro().getId() : null, d.isOrigemContaFlag());
    }
}
