package com.casacapital.backend.historicoprecos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Comparação de preços entre os mercados das notas fiscais importadas. */
public record ComparacaoMercadosResponse(
        List<Mercado> mercados,
        List<Produto> produtos,   // só os encontrados em pelo menos 2 mercados
        Lista lista,
        List<Alta> altas
) {
    public record Mercado(String nome, int compras, LocalDate ultimaCompra, int produtos) {
    }

    /** precos: último preço em cada mercado, do mais barato para o mais caro. */
    public record Produto(String nome, String codigoBarras, List<PrecoMercado> precos, String maisBarato,
                          BigDecimal economia, Double economiaPct) {
    }

    public record PrecoMercado(String mercado, BigDecimal preco, LocalDate data) {
    }

    /** A lista de compras atual estimada em cada mercado. */
    public record Lista(List<ItemLista> itens, List<TotalMercado> totais, int itensCestaComum) {
    }

    /** produto = nome do produto das notas usado para o item da lista (null se não achou). */
    public record ItemLista(String nome, int qtd, String produto, List<PrecoMercado> precos) {
    }

    /** totalCestaComum: só os itens que têm preço em todos os mercados (comparação justa). */
    public record TotalMercado(String mercado, BigDecimal total, int itensComPreco, int itensSemPreco,
                               BigDecimal totalCestaComum) {
    }

    public record Alta(String nome, String mercado, BigDecimal precoAnterior, LocalDate dataAnterior,
                       BigDecimal precoAtual, LocalDate data, double pct) {
    }
}
