package com.casacapital.backend.compra.dto;

import com.casacapital.backend.compra.Compra;
import com.casacapital.backend.compra.Frequencia;

public record CompraResponse(Long id, String nome, Integer qtd, String categoria, Frequencia frequencia) {
    public static CompraResponse from(Compra c) {
        return new CompraResponse(c.getId(), c.getNome(), c.getQtd(), c.getCategoria(), c.getFrequencia());
    }
}
