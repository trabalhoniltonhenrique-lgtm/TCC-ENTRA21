package com.casacapital.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequest(
        @NotBlank(message = "token inválido") String token,
        @NotBlank @Size(min = 6, message = "a senha deve ter ao menos 6 caracteres") String novaSenha
) {
}
