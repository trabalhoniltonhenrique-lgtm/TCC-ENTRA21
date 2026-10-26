package com.casacapital.backend.mesada.dto;

import com.casacapital.backend.mesada.MesadaMovimento;
import com.casacapital.backend.mesada.TipoMovimentoMesada;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimentoMesadaResponse(Long id, TipoMovimentoMesada tipo, BigDecimal valor, String descricao,
                                      LocalDate data, String criadoPor) {
    public static MovimentoMesadaResponse from(MesadaMovimento m) {
        return new MovimentoMesadaResponse(m.getId(), m.getTipo(), m.getValor(), m.getDescricao(), m.getData(),
                m.getCriadoPor());
    }
}
