package com.casacapital.backend.orcamento.dto;

import com.casacapital.backend.orcamento.OrcamentoMensal;

import java.math.BigDecimal;

public record OrcamentoItemResponse(String categoria, BigDecimal limite) {
    public static OrcamentoItemResponse from(OrcamentoMensal o) {
        return new OrcamentoItemResponse(o.getCategoria(), o.getLimite());
    }
}
