package com.casacapital.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void arquivoInexistenteDa404() {
        var r = handler.handleUnexpected(new NoResourceFoundException(HttpMethod.GET, "/js/orcamento.js", "js/orcamento.js"));
        assertEquals(404, r.getStatusCode().value());
        assertEquals("NOT_FOUND", r.getBody().codigo());
    }

    @Test
    void metodoNaoPermitidoDa405EParametroFaltandoDa400() {
        assertEquals(405, handler.handleUnexpected(new HttpRequestMethodNotSupportedException("DELETE")).getStatusCode().value());
        assertEquals(400, handler.handleUnexpected(new MissingServletRequestParameterException("arquivo", "MultipartFile"))
                .getStatusCode().value());
    }

    @Test
    void erroDeVerdadeContinuaSendo500() {
        var r = handler.handleUnexpected(new IllegalStateException("falha"));
        assertEquals(500, r.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", r.getBody().codigo());
    }
}
