package com.casacapital.backend.assinatura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Assinaturas detectadas nas despesas. totalMensal/totalAnual somam só as ativas; ignoradas = "não é assinatura". */
public record AssinaturasResponse(BigDecimal totalMensal, BigDecimal totalAnual, List<Assinatura> assinaturas,
                                  List<String> ignoradas) {

    /**
     * ativa: cobrada nos últimos 45 dias. nova: primeira cobrança nos últimos 45 dias.
     * aumentoPct: quanto a última cobrança subiu em relação à anterior (null se não subiu).
     * provavel: serviço conhecido visto uma vez só (ainda não deu para confirmar que se repete).
     */
    public record Assinatura(String chave, String nome, String categoria, BigDecimal valor, BigDecimal valorAnterior,
                             BigDecimal aumentoPct, LocalDate primeiraCobranca, LocalDate ultimaCobranca, int meses,
                             boolean ativa, boolean nova, boolean provavel) {
    }
}
