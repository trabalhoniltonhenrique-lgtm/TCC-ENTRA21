package com.casacapital.backend.receita.dto;

import com.casacapital.backend.receita.Receita;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReceitaResponse(
        Long id,
        String nome,
        BigDecimal valor,
        String categoria,
        LocalDate data,
        Long membroId
) {
    public static ReceitaResponse from(Receita r) {
        return new ReceitaResponse(r.getId(), r.getNome(), r.getValor(), r.getCategoria(), r.getData(),
                r.getMembro() != null ? r.getMembro().getId() : null);
    }
}
