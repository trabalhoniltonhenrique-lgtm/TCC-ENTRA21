package com.casacapital.backend.usuario;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 180, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 60)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerfilUsuario perfil = PerfilUsuario.RESPONSAVEL;

    /** Só no dependente: o membro da família a quem o login pertence. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membro_id")
    private MembroFamilia membro;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Usuario(Familia familia, String nome, String email, String senhaHash) {
        this.familia = familia;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
