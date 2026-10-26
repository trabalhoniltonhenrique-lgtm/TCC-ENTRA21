package com.casacapital.backend.mesada;

/** MESADA: crédito automático do mês; EXTRA: dinheiro a mais dado pelos pais; GASTO: saída. */
public enum TipoMovimentoMesada {
    MESADA,
    EXTRA,
    GASTO;

    public boolean entrada() {
        return this != GASTO;
    }
}
