package com.casacapital.backend.mesada;

import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.membro.MembroFamilia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Mesada de um membro: valor creditado todo mês no dia escolhido, e um objetivo opcional. */
@Entity
@Table(name = "mesada")
@Getter
@Setter
@NoArgsConstructor
public class Mesada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membro_id", nullable = false)
    private MembroFamilia membro;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(name = "dia_pagamento", nullable = false)
    private Integer diaPagamento;

    @Column(nullable = false)
    private boolean ativa = true;

    /** A partir de quando a mesada é creditada (meses anteriores não geram crédito). */
    @Column(nullable = false)
    private LocalDate inicio;

    @Column(name = "meta_nome", length = 120)
    private String metaNome;

    @Column(name = "meta_valor")
    private BigDecimal metaValor;
}
