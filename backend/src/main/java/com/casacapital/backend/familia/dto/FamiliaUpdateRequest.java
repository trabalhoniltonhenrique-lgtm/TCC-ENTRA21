package com.casacapital.backend.familia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FamiliaUpdateRequest(
        @NotBlank String nomeFamilia,
        String cidade,
        String responsavel,
        String moeda,
        String separadorDecimal,
        @Min(1) @Max(31) Integer diaFechamento,
        Boolean mostrarSaldo,
        Boolean alertaContas,
        Boolean confirmarExclusao,
        Boolean agruparCat
) {
}
