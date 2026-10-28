package com.casacapital.backend.familia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Foto da família (uma por família), já reduzida e regravada em JPEG pelo {@link FamiliaFotoService}. */
@Entity
@Table(name = "familia_foto")
@Getter
@Setter
@NoArgsConstructor
public class FamiliaFoto {

    @Id
    @Column(name = "familia_id")
    private Long familiaId;

    @Column(nullable = false, length = 40)
    private String tipo;

    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] conteudo;

    public FamiliaFoto(Long familiaId) {
        this.familiaId = familiaId;
    }
}
