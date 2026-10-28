package com.casacapital.backend.familia.dto;

import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.PlanoFamilia;

import java.time.LocalDateTime;

public record FamiliaResponse(
        Long id,
        String nomeFamilia,
        String cidade,
        String responsavel,
        PlanoFamilia plano,
        String moeda,
        String separadorDecimal,
        Integer diaFechamento,
        Boolean mostrarSaldo,
        Boolean alertaContas,
        Boolean confirmarExclusao,
        Boolean agruparCat,
        String corPrimaria,
        // null = sem foto; muda a cada troca, para o navegador saber que a cópia guardada ficou velha
        LocalDateTime fotoAtualizadaEm
) {
    public static FamiliaResponse from(Familia f) {
        return new FamiliaResponse(
                f.getId(),
                f.getNomeFamilia(),
                f.getCidade(),
                f.getResponsavel(),
                f.getPlano(),
                f.getMoeda(),
                f.getSeparadorDecimal(),
                f.getDiaFechamento(),
                f.getMostrarSaldo(),
                f.getAlertaContas(),
                f.getConfirmarExclusao(),
                f.getAgruparCat(),
                f.getCorPrimaria(),
                f.getFotoAtualizadaEm()
        );
    }
}
