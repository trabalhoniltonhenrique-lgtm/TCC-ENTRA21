package com.casacapital.backend.fatura;

import com.casacapital.backend.fatura.LeitorContaConsumo.ContaLida;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeitorContaConsumoTest {

    @Test
    void leContaDeLuzComValorAbaixoDoRotulo() {
        // Layout da Celesc: o valor vem algumas linhas abaixo de "Total a Pagar", depois do código de débito automático
        Optional<ContaLida> lida = LeitorContaConsumo.ler(List.of("""
                RESIDENCIAL - B1 Residencial - BIFÁSICO
                10/2026 20/10/2026  98,70 R$
                (0P) Consumo TE KWH 100,000 0,398929 39,89
                (75) Band Amarela KWH 100,000 0,021000 2,10
                TOTAL 98,70
                Beneficiário: Celesc Distribuição SA
                Referência
                10/2026
                Vencimento
                20/10/2026
                Total a Pagar (R$)
                6001171417
                98,70
                """));

        assertTrue(lida.isPresent());
        ContaLida c = lida.get();
        assertEquals("Energia", c.categoria());
        assertEquals("Energia elétrica - Celesc 10/2026", c.descricao());
        assertEquals(new BigDecimal("98.70"), c.valor());
        assertEquals(LocalDate.of(2026, 10, 20), c.vencimento());
        assertEquals("10/2026", c.referencia());
    }

    @Test
    void leContaDeAgua() {
        ContaLida c = LeitorContaConsumo.ler(List.of("""
                CASAN - Companhia Catarinense de Águas e Saneamento
                Consumo de água 12 m3   Esgoto
                Data de vencimento: 25/10/2026
                Valor a pagar R$ 1.087,15
                """)).orElseThrow();

        assertEquals("Água", c.categoria());
        assertEquals(new BigDecimal("1087.15"), c.valor());
        assertEquals(LocalDate.of(2026, 10, 25), c.vencimento());
    }

    @Test
    void faturaDeCartaoNaoEContaDeConsumo() {
        assertTrue(LeitorContaConsumo.ler(List.of("""
                Vencimento 13/10/2026 Total desta fatura 151,81
                13/09 CELESC DISTRIBUICAO 98,70
                22/09 PADARIA CENTRO 20,00
                """)).isEmpty());
    }
}
