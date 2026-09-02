package com.casacapital.backend.familia.dto;

import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.PlanoFamilia;

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
        Boolean agruparCat
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
                f.getAgruparCat()
        );
    }
}
