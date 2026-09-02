package com.casacapital.backend.meta;

import com.casacapital.backend.familia.Familia;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meta")
@Getter
@Setter
@NoArgsConstructor
public class Meta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(name = "valor_alvo", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorAlvo;

    private LocalDate prazo;

    @Column(nullable = false)
    private boolean concluida = false;

    @OneToMany(mappedBy = "meta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("data ASC")
    private List<MetaAporte> aportes = new ArrayList<>();

    public BigDecimal totalPoupado() {
        return aportes.stream().map(MetaAporte::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
