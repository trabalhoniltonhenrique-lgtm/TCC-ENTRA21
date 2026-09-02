package com.casacapital.backend.orcamento;

import com.casacapital.backend.familia.Familia;
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

@Entity
@Table(name = "orcamento_mensal")
@Getter
@Setter
@NoArgsConstructor
public class OrcamentoMensal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(name = "mes_ano", nullable = false, length = 7)
    private String mesAno;

    @Column(nullable = false, length = 40)
    private String categoria;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal limite;
}
