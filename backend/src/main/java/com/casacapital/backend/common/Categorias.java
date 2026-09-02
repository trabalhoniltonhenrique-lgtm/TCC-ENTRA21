package com.casacapital.backend.common;

import java.util.Set;

/** Espelha as categorias fixas do front-end (js/app.js). */
public final class Categorias {

    public static final Set<String> RECEITA = Set.of(
            "Salário", "Freelance", "Aluguel recebido", "Investimentos", "Outros");

    public static final Set<String> DESPESA = Set.of(
            "Alimentação", "Moradia", "Transporte", "Saúde", "Educação",
            "Lazer", "Vestuário", "Contas & Serviços", "Outros");

    private Categorias() {
    }

    public static void validar(Set<String> categorias, String valor) {
        if (valor == null || !categorias.contains(valor)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "CATEGORIA_INVALIDA", "Categoria inválida: " + valor);
        }
    }
}
