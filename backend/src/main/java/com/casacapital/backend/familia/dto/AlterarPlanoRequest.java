package com.casacapital.backend.familia.dto;

import com.casacapital.backend.familia.PlanoFamilia;
import jakarta.validation.constraints.NotNull;

public record AlterarPlanoRequest(@NotNull PlanoFamilia plano) {
}
