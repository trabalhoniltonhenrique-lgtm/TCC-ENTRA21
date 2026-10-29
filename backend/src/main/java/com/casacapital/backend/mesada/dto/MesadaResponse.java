package com.casacapital.backend.mesada.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Situação da mesada de um membro. configurada=false quando ainda não há valor definido
 * (o membro aparece na lista para os pais poderem configurar).
 */
public record MesadaResponse(
        Long membroId,
        String nome,
        String cor,
        String parentesco, // a tela mostra os filhos e esconde os demais membros
        boolean configurada,
        BigDecimal valor,
        Integer diaPagamento,
        boolean ativa,
        LocalDate proximaMesada,
        String metaNome,
        BigDecimal metaValor,
        Integer metaPct,
        BigDecimal saldo,
        BigDecimal recebidoNoMes,
        BigDecimal gastoNoMes,
        String login,
        List<MovimentoMesadaResponse> movimentos
) {
}
