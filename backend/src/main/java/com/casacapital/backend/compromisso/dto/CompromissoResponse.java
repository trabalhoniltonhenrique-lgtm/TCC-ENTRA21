package com.casacapital.backend.compromisso.dto;

import com.casacapital.backend.compromisso.Compromisso;

import java.time.LocalDateTime;

public record CompromissoResponse(Long id, String titulo, LocalDateTime inicio, String local, String obs,
                                  Integer antecedenciaMin, Long membroId, String membroNome, boolean avisoDispensado,
                                  LocalDateTime momentoDoAviso) {
    public static CompromissoResponse from(Compromisso c) {
        return new CompromissoResponse(c.getId(), c.getTitulo(), c.getInicio(), c.getLocal(), c.getObs(),
                c.getAntecedenciaMin(), c.getMembro() != null ? c.getMembro().getId() : null,
                c.getMembro() != null ? c.getMembro().getNome() : null, c.isAvisoDispensado(), c.momentoDoAviso());
    }
}
