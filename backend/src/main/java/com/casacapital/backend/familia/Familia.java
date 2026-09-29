package com.casacapital.backend.familia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "familia")
@Getter
@Setter
@NoArgsConstructor
public class Familia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_familia", nullable = false, length = 120)
    private String nomeFamilia;

    @Column(length = 120)
    private String cidade;

    @Column(length = 120)
    private String responsavel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanoFamilia plano = PlanoFamilia.ESSENCIAL;

    @Column(nullable = false, length = 5)
    private String moeda = "R$";

    @Column(name = "separador_decimal", nullable = false, length = 1)
    private String separadorDecimal = ",";

    @Column(name = "dia_fechamento", nullable = false)
    private Integer diaFechamento = 1;

    @Column(name = "mostrar_saldo", nullable = false)
    private Boolean mostrarSaldo = true;

    @Column(name = "alerta_contas", nullable = false)
    private Boolean alertaContas = true;

    @Column(name = "confirmar_exclusao", nullable = false)
    private Boolean confirmarExclusao = true;

    @Column(name = "agrupar_cat", nullable = false)
    private Boolean agruparCat = false;

    /** Cor principal do layout em hexadecimal (#RRGGBB); null usa o azul padrão. */
    @Column(name = "cor_primaria", length = 7)
    private String corPrimaria;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Familia(String nomeFamilia) {
        this.nomeFamilia = nomeFamilia;
    }

    @PrePersist
    void onCreate() {
        Instant agora = Instant.now();
        this.createdAt = agora;
        this.updatedAt = agora;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isPremium() {
        return plano == PlanoFamilia.PREMIUM;
    }
}
