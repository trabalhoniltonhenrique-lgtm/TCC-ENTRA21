package com.casacapital.backend.fechamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resumo de um mês: entradas e saídas, comparação com o mês anterior, categorias, metas, assinaturas e patrimônio. */
public record FechamentoResponse(
        String mes,
        BigDecimal receitas,
        BigDecimal despesas,
        BigDecimal saldo,
        BigDecimal receitasAnterior,
        BigDecimal despesasAnterior,
        BigDecimal variacaoDespesasPct,
        List<Categoria> categorias,
        List<Despesa> maioresDespesas,
        List<Orcamento> orcamento,
        List<Meta> metas,
        Assinaturas assinaturas,
        Patrimonio patrimonio,
        ContasPagas contasPagas
) {
    /** variacaoPct: em relação ao mês anterior (null se não houve gasto na categoria no mês anterior). */
    public record Categoria(String categoria, BigDecimal valor, BigDecimal anterior, BigDecimal variacaoPct) {
    }

    public record Despesa(LocalDate data, String nome, String categoria, BigDecimal valor) {
    }

    public record Orcamento(String categoria, BigDecimal limite, BigDecimal gasto, boolean estourou) {
    }

    public record Meta(String nome, BigDecimal aportadoNoMes, BigDecimal totalPoupado, BigDecimal valorAlvo, BigDecimal pct) {
    }

    public record Assinaturas(int quantidade, BigDecimal total, List<Assinatura> itens) {
    }

    public record Assinatura(String nome, BigDecimal valor, BigDecimal aumentoPct) {
    }

    /** anterior/variacao: null quando não há foto do patrimônio do mês anterior. */
    public record Patrimonio(BigDecimal total, BigDecimal anterior, BigDecimal variacao) {
    }

    public record ContasPagas(int quantidade, BigDecimal total) {
    }
}
