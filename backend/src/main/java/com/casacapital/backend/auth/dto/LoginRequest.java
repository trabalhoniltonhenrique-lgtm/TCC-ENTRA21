package com.casacapital.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email(message = "e-mail inválido") String email,
        @NotBlank String senha
) {
}
