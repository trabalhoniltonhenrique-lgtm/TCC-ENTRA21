package com.casacapital.backend.historicoprecos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProdutoHistoricoResponse(
        String produtoChave,
        String nome,
        List<HistoricoPontoResponse> registros,
        BigDecimal precoAtual,
        LocalDate dataUltimo,
        VariacaoResponse variacao
) {
}
