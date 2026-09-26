package com.casacapital.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EsqueciSenhaRequest(
        @NotBlank @Email(message = "e-mail inválido") String email
) {
}
