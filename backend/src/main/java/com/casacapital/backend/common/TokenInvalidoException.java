package com.casacapital.backend.common;

import org.springframework.http.HttpStatus;

public class TokenInvalidoException extends ApiException {

    public TokenInvalidoException() {
        super(HttpStatus.BAD_REQUEST, "TOKEN_INVALIDO", "Link de redefinição inválido ou expirado.");
    }
}
