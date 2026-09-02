package com.casacapital.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarRequest(
        @NotBlank(message = "informe o nome da família") String nomeFamilia,
        @NotBlank(message = "informe seu nome") String nome,
        @NotBlank @Email(message = "e-mail inválido") String email,
        @NotBlank @Size(min = 6, message = "a senha deve ter ao menos 6 caracteres") String senha
) {
}
