package com.casacapital.backend.impostorenda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Dados do ano para ajudar na declaração do Imposto de Renda (não substitui a conferência com as regras da Receita). */
public record ImpostoRendaResponse(
        int ano,
        List<Integer> anosDisponiveis,
        Rendimentos rendimentos,
        Dedutiveis saude,
        Dedutiveis educacao,
        List<Bem> bens
) {
    public record Rendimentos(BigDecimal total, List<Total> porCategoria, List<Total> porPessoa) {
    }

    public record Total(String nome, BigDecimal valor) {
    }

    /** totalProvavel: soma dos itens provavelmente dedutíveis. */
    public record Dedutiveis(BigDecimal total, BigDecimal totalProvavel, List<Total> porPessoa, List<Item> itens) {
    }

    /** situacao: PROVAVEL (provavelmente dedutível), NAO (em geral não dedutível) ou CONFERIR. */
    public record Item(LocalDate data, String descricao, String pessoa, BigDecimal valor, String situacao, String motivo) {
    }

    /** Bens e direitos: investimentos aplicados até 31/12 do ano (valor aplicado = custo; o saldo em 31/12 vem do informe do banco). */
    public record Bem(String nome, String tipo, String instituicao, LocalDate dataAplicacao, BigDecimal valorAplicado,
                      BigDecimal valorAtual) {
    }
}
