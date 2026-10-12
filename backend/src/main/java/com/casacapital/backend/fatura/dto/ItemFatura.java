package com.casacapital.backend.fatura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lançamento lido da fatura, já comparado com o que existe no sistema.
 * acao: NOVA_DESPESA (à vista), NOVA_PARCELADA (cria as parcelas), QUITAR_PARCELA (parcela já cadastrada
 * em Contas — será marcada como paga) ou JA_IMPORTADA (já existe; vem desmarcado na revisão).
 */
public record ItemFatura(
        LocalDate data,
        String descricao,
        BigDecimal valor,
        Integer parcelaAtual,
        Integer parcelaTotal,
        String categoria,
        String acao,
        Long contaId,
        String aviso
) {
}
