package com.casacapital.backend.nfce;

/** Expõe para testes de outro pacote a validação de código de barras do NfceService. */
public final class NfceServiceGtin {

    private NfceServiceGtin() {
    }

    public static String gtin(String codigo) {
        return NfceService.gtin(codigo);
    }
}
