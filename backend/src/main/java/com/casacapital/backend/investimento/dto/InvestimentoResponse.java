package com.casacapital.backend.investimento.dto;

import com.casacapital.backend.investimento.Investimento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestimentoResponse(
        Long id,
        String nome,
        String tipo,
        String instituicao,
        BigDecimal valorAplicado,
        BigDecimal valorAtual,
        BigDecimal taxa,
        String tipoTaxa,
        LocalDate dataAplicacao,
        LocalDate vencimento,
        String obs,
        LocalDate atualizadoEm,
        Long metaId,
        String metaNome
) {
    public static InvestimentoResponse from(Investimento i) {
        return new InvestimentoResponse(i.getId(), i.getNome(), i.getTipo(), i.getInstituicao(), i.getValorAplicado(),
                i.getValorAtual(), i.getTaxa(), i.getTipoTaxa(), i.getDataAplicacao(), i.getVencimento(), i.getObs(), i.getAtualizadoEm(),
                i.getMeta() != null ? i.getMeta().getId() : null,
                i.getMeta() != null ? i.getMeta().getNome() : null);
    }
}
