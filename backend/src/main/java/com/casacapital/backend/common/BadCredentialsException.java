package com.casacapital.backend.common;

import org.springframework.http.HttpStatus;

public class BadCredentialsException extends ApiException {

    public BadCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Login ou senha inválidos.");
    }
}
