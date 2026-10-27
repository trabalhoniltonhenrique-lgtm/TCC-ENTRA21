package com.casacapital.backend.compromisso;

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

import java.time.LocalDateTime;

/** Compromisso com data e hora (consulta, reunião...), diferente das tarefas do dia a dia. */
@Entity
@Table(name = "compromisso")
@Getter
@Setter
@NoArgsConstructor
public class Compromisso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membro_id")
    private MembroFamilia membro;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column(length = 160)
    private String local;

    @Column(columnDefinition = "TEXT")
    private String obs;

    /** Quantos minutos antes do horário o alerta dispara (0 = na hora). */
    @Column(name = "antecedencia_min", nullable = false)
    private Integer antecedenciaMin = 15;

    @Column(name = "aviso_dispensado", nullable = false)
    private boolean avisoDispensado = false;

    @Column(name = "adiado_ate")
    private LocalDateTime adiadoAte;

    /** Quando o alerta deve aparecer: o horário menos a antecedência, ou o "lembrar depois". */
    public LocalDateTime momentoDoAviso() {
        return adiadoAte != null ? adiadoAte : inicio.minusMinutes(antecedenciaMin);
    }
}
