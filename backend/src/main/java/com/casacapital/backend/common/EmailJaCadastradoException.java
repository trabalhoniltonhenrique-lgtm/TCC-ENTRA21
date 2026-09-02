package com.casacapital.backend.common;

import org.springframework.http.HttpStatus;

public class EmailJaCadastradoException extends ApiException {

    public EmailJaCadastradoException() {
        super(HttpStatus.CONFLICT, "EMAIL_EM_USO", "Já existe uma conta cadastrada com este e-mail.");
    }
}
