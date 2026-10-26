package com.casacapital.backend.planejamento.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Projeção de saldo mês a mês. Entradas e gastos do dia a dia usam a média dos últimos 3 meses; contas são as
 * cadastradas em aberto (inclusive parcelas do cartão) mais as próximas ocorrências das contas mensais/anuais.
 */
public record FluxoCaixaResponse(
        BigDecimal saldoAtual,
        BigDecimal mediaReceitas,
        BigDecimal mediaGastosVariaveis,
        int mesesDeHistorico,
        String primeiroMesNegativo,
        List<Mes> meses
) {
    public record Mes(
            String mes,
            BigDecimal receitas,
            BigDecimal contas,
            BigDecimal parcelas,
            BigDecimal gastosVariaveis,
            BigDecimal saldoFinal,
            List<Item> itens
    ) {
    }

    /** prevista = próxima ocorrência de uma conta mensal/anual que ainda não foi gerada. */
    public record Item(String descricao, BigDecimal valor, LocalDate vencimento, boolean parcela, boolean prevista) {
    }
}
