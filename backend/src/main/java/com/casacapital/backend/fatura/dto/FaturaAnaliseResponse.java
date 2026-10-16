package com.casacapital.backend.fatura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * tipo CARTAO: fatura do cartão — itens/creditos preenchidos.
 * tipo CONTA_CONSUMO: conta de luz/água — vira uma conta a pagar, descrita em "conta".
 */
public record FaturaAnaliseResponse(
        String tipo,
        String emissor,
        LocalDate vencimento,
        BigDecimal total,
        List<ItemFatura> itens,
        List<ItemFatura> creditos,
        ContaConsumo conta
) {
    public static FaturaAnaliseResponse cartao(String emissor, LocalDate vencimento, BigDecimal total,
                                               List<ItemFatura> itens, List<ItemFatura> creditos) {
        return new FaturaAnaliseResponse("CARTAO", emissor, vencimento, total, itens, creditos, null);
    }

    public static FaturaAnaliseResponse contaConsumo(ContaConsumo conta) {
        return new FaturaAnaliseResponse("CONTA_CONSUMO", null, conta.vencimento(), conta.valor(), List.of(), List.of(), conta);
    }

    /**
     * contaId: conta em aberto da mesma categoria e mês que será atualizada (em vez de criar outra);
     * jaCadastrada: já existe conta igual (mesmo valor e vencimento).
     */
    public record ContaConsumo(
            String descricao,
            String categoria,
            BigDecimal valor,
            LocalDate vencimento,
            String referencia,
            Long contaId,
            String contaExistenteDescricao,
            boolean jaCadastrada
    ) {
    }
}
