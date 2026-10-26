package com.casacapital.backend.planejamento.dto;

import java.math.BigDecimal;
import java.util.List;

/** total = saldo (receitas − despesas lançadas) + investimentos (valor atual) − dívidas (parcelas a vencer). */
public record PatrimonioResponse(Foto atual, List<Foto> historico) {

    public record Foto(String mes, BigDecimal saldo, BigDecimal investimentos, BigDecimal dividas, BigDecimal total) {
    }
}
