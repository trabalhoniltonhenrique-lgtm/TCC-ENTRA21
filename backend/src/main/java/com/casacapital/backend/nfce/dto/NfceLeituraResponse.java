package com.casacapital.backend.nfce.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Nota lida, com as sugestões do sistema para a revisão (nada foi gravado ainda).
 * precisaColar=true quando o site da Fazenda pediu a verificação "não sou um robô": a pessoa abre o link
 * no navegador e cola o texto da página (itens vazios até lá).
 */
public record NfceLeituraResponse(
        String link,
        boolean precisaColar,
        String chave,
        String emitente,
        String cnpj,
        LocalDate dataEmissao,
        BigDecimal total,
        LocalDateTime importadaEm, // preenchido se esta nota já foi importada antes
        List<Item> itens
) {
    public record Item(
            String nome,
            String codigo,
            BigDecimal quantidade,
            String unidade,
            BigDecimal valorUnitario,
            BigDecimal valorTotal,
            String categoria,       // sugestão de categoria da despensa
            Integer qtdDespensa,    // quantas unidades entram na despensa (produto a granel conta 1)
            BigDecimal precoAnterior,
            LocalDate dataPrecoAnterior
    ) {
    }
}
