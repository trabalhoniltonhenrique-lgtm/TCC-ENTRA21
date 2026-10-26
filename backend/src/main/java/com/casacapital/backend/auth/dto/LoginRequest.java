package com.casacapital.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String email, // e-mail, ou o nome de usuário do filho
        @NotBlank String senha
) {
}
