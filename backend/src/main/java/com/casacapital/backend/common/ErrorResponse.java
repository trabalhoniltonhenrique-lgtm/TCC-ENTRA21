package com.casacapital.backend.common;

import java.time.Instant;

public record ErrorResponse(String codigo, String mensagem, Instant timestamp) {

    public static ErrorResponse of(String codigo, String mensagem) {
        return new ErrorResponse(codigo, mensagem, Instant.now());
    }
}
