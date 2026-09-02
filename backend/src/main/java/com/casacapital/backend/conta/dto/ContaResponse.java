package com.casacapital.backend.conta.dto;

import com.casacapital.backend.conta.Conta;
import com.casacapital.backend.conta.Recorrencia;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContaResponse(
        Long id,
        String descricao,
        String categoria,
        BigDecimal valor,
        LocalDate vencimento,
        boolean pago,
        LocalDate dataPagamento,
        Recorrencia recorrencia,
        String obs,
        Long grupoParcelaId,
        Integer parcelaNum,
        Integer parcelaTotal
) {
    public static ContaResponse from(Conta c) {
        return new ContaResponse(c.getId(), c.getDescricao(), c.getCategoria(), c.getValor(), c.getVencimento(),
                c.isPago(), c.getDataPagamento(), c.getRecorrencia(), c.getObs(),
                c.getGrupoParcelaId(), c.getParcelaNum(), c.getParcelaTotal());
    }
}
