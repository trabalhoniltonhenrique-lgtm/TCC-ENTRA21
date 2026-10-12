package com.casacapital.backend.fatura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeitorFaturaIATest {

    @Test
    void montaRequisicaoComEsquemaValido() {
        LeitorFaturaIA leitor = new LeitorFaturaIA("chave-teste", "claude-opus-5-5");
        assertDoesNotThrow(() -> leitor.montarParametros("%PDF-1.4".getBytes(), LeitorFaturaIA.TipoArquivo.PDF));
        assertDoesNotThrow(() -> leitor.montarParametros(new byte[]{1, 2, 3}, LeitorFaturaIA.TipoArquivo.PNG));
    }

    @Test
    void comparaEstabelecimento() {
        assertTrue(FaturaService.mesmaLoja("MAGAZINE LUIZA*SP", "Magazine Luiza"));
        assertTrue(FaturaService.mesmaLoja("Drogaria São Paulo", "DROGARIA SAO PAULO 123"));
        assertFalse(FaturaService.mesmaLoja("NETFLIX", "SPOTIFY"));
        assertFalse(FaturaService.mesmaLoja("AB", "AB"));
    }
}
