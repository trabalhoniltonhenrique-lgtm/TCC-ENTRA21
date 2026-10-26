package com.casacapital.backend.rede;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedeControllerTest {

    @Test
    void qrCodeEhUmaMatrizQuadrada() {
        List<String> qr = RedeController.qr("http://192.168.0.10:8080");
        assertTrue(qr.size() >= 21); // menor QR code é 21x21
        assertTrue(qr.stream().allMatch(l -> l.length() == qr.size() && l.matches("[01]+")));
        assertEquals("1111111", qr.get(0).substring(0, 7)); // marcador do canto
    }

    @Test
    @SuppressWarnings("unchecked")
    void listaSoEnderecosDaRedeLocal() throws Exception {
        Map<String, Object> r = new RedeController(8080).enderecos();
        assertEquals(8080, r.get("porta"));
        for (RedeController.Endereco e : (List<RedeController.Endereco>) r.get("enderecos")) {
            assertTrue(e.url().matches("http://(10|172|192)\\.[0-9.]+:8080"), e.url());
        }
    }
}
