package com.casacapital.backend.mesada;

import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.membro.MembroFamilia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "mesada_movimento")
@Getter
@Setter
@NoArgsConstructor
public class MesadaMovimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membro_id", nullable = false)
    private MembroFamilia membro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimentoMesada tipo;

    /** Sempre positivo; o tipo diz se entra ou sai. */
    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false, length = 160)
    private String descricao;

    @Column(nullable = false)
    private LocalDate data;

    /** AAAA-MM, só nas mesadas automáticas (uma por mês). */
    @Column(length = 7)
    private String referencia;

    @Column(name = "criado_por", length = 120)
    private String criadoPor;
}
