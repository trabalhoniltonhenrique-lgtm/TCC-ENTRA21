package com.casacapital.backend.common;

import org.springframework.http.HttpStatus;

public class PremiumRequiredException extends ApiException {

    public PremiumRequiredException() {
        super(HttpStatus.FORBIDDEN, "PREMIUM_REQUIRED",
                "Este recurso está disponível apenas para famílias com o plano Premium.");
    }
}
