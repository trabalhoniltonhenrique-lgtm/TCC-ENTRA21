package com.casacapital.backend.nfce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** O que foi lido da página da NFC-e, antes de qualquer sugestão do sistema. */
record NotaFiscalLida(String chave, String emitente, String cnpj, LocalDate dataEmissao, BigDecimal total,
                      List<Item> itens) {

    record Item(String nome, String codigo, BigDecimal quantidade, String unidade, BigDecimal valorUnitario,
                BigDecimal valorTotal) {
    }
}
