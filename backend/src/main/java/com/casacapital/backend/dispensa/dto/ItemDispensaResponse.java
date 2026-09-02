package com.casacapital.backend.dispensa.dto;

import com.casacapital.backend.dispensa.ItemDispensa;

import java.time.LocalDate;

public record ItemDispensaResponse(
        Long id,
        String nome,
        Integer qtd,
        String categoria,
        LocalDate dataEntrada,
        LocalDate dataAtualizacao
) {
    public static ItemDispensaResponse from(ItemDispensa i) {
        return new ItemDispensaResponse(i.getId(), i.getNome(), i.getQtd(), i.getCategoria(),
                i.getDataEntrada(), i.getDataAtualizacao());
    }
}
