package com.casacapital.backend.mesada.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Login do filho, criado pelos pais. Na alteração, senha vazia mantém a atual. */
public record AcessoDependenteRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,40}",
                message = "o usuário deve ter de 3 a 40 letras, números, ponto, hífen ou sublinhado (sem espaços nem @)")
        String login,
        @Size(min = 6, max = 72, message = "a senha precisa ter pelo menos 6 caracteres") String senha
) {
}
