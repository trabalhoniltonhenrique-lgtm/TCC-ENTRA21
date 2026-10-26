package com.casacapital.backend.planejamento;

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
import java.time.LocalDate;

/** Foto do patrimônio da família num mês ("YYYY-MM"); a do mês corrente é atualizada a cada consulta. */
@Entity
@Table(name = "patrimonio_mensal")
@Getter
@Setter
@NoArgsConstructor
public class PatrimonioMensal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(nullable = false, length = 7)
    private String mes;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal saldo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal investimentos;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal dividas;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDate atualizadoEm;
}
