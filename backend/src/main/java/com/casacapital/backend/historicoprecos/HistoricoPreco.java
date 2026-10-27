package com.casacapital.backend.historicoprecos;

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

@Entity
@Table(name = "historico_preco")
@Getter
@Setter
@NoArgsConstructor
public class HistoricoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "familia_id", nullable = false)
    private Familia familia;

    @Column(name = "produto_chave", nullable = false, length = 180)
    private String produtoChave;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    /** Mercado da compra (nome do emitente da nota fiscal); vazio nas compras registradas à mão. */
    @Column(length = 160)
    private String mercado;

    /** Código de barras (GTIN/EAN) vindo da nota fiscal, quando é um código válido. */
    @Column(name = "codigo_barras", length = 14)
    private String codigoBarras;

    public HistoricoPreco(Familia familia, String produtoChave, LocalDate data, BigDecimal preco) {
        this.familia = familia;
        this.produtoChave = produtoChave;
        this.data = data;
        this.preco = preco;
    }

    public static String chave(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase();
    }
}
