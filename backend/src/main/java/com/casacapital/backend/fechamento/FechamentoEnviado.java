package com.casacapital.backend.fechamento;

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

import java.time.LocalDateTime;

/** Mês cujo fechamento já foi enviado por e-mail à família (para não enviar de novo). */
@Entity
@Table(name = "fechamento_enviado")
@Getter
@Setter
@NoArgsConstructor
public class FechamentoEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(nullable = false, length = 7)
    private String mes;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;
}
