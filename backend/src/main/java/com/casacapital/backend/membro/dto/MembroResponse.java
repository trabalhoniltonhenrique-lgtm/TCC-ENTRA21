package com.casacapital.backend.membro.dto;

import com.casacapital.backend.membro.MembroFamilia;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MembroResponse(
        Long id,
        String nome,
        String parentesco,
        LocalDate nascimento,
        BigDecimal renda,
        String obs,
        String cor
) {
    public static MembroResponse from(MembroFamilia m) {
        return new MembroResponse(m.getId(), m.getNome(), m.getParentesco(), m.getNascimento(),
                m.getRenda(), m.getObs(), m.getCor());
    }
}
