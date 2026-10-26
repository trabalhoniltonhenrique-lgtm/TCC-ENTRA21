package com.casacapital.backend.investimento;

import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.meta.Meta;
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

/** Uma aplicação da carteira (CDB, Tesouro, ações…). O valor atual é informado pela família. */
@Entity
@Table(name = "investimento")
@Getter
@Setter
@NoArgsConstructor
public class Investimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    /** Meta que este investimento ajuda a cumprir (opcional). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_id")
    private Meta meta;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 30)
    private String tipo;

    @Column(length = 60)
    private String instituicao;

    @Column(name = "valor_aplicado", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorAplicado;

    @Column(name = "valor_atual", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorAtual;

    @Column(name = "data_aplicacao")
    private LocalDate dataAplicacao;

    private LocalDate vencimento;

    @Column(length = 255)
    private String obs;

    /** Quando o valor atual foi informado pela última vez. */
    @Column(name = "atualizado_em", nullable = false)
    private LocalDate atualizadoEm;
}
