package com.casacapital.backend.analise.dto;

import java.math.BigDecimal;
import java.util.List;

public record AnaliseResumoResponse(
        BigDecimal saldoAtual,
        List<SerieMensalResponse> seriesMensal,
        List<CategoriaValorResponse> despesasPorCategoria,
        List<TendenciaCategoriaResponse> tendenciaCategorias
) {
}
