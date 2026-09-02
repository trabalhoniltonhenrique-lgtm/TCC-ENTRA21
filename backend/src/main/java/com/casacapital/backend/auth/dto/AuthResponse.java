package com.casacapital.backend.auth.dto;

import com.casacapital.backend.familia.dto.FamiliaResponse;

public record AuthResponse(
        String token,
        Long usuarioId,
        String nome,
        String email,
        FamiliaResponse familia
) {
}
