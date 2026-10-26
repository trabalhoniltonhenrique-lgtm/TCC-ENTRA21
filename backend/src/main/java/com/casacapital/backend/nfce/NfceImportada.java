package com.casacapital.backend.nfce;

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
import java.time.LocalDateTime;

/** Nota fiscal do mercado já importada (pela chave de acesso), para avisar se a mesma nota for lida de novo. */
@Entity
@Table(name = "nfce_importada")
@Getter
@Setter
@NoArgsConstructor
public class NfceImportada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(nullable = false, length = 44)
    private String chave;

    @Column(length = 160)
    private String emitente;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    private BigDecimal total;

    @Column(nullable = false)
    private Integer itens;

    @Column(name = "importada_em", nullable = false)
    private LocalDateTime importadaEm;
}
