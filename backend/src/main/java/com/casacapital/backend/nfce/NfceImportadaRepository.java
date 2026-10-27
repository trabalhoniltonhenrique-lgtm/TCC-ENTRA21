package com.casacapital.backend.nfce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NfceImportadaRepository extends JpaRepository<NfceImportada, Long> {

    Optional<NfceImportada> findByFamiliaIdAndChave(Long familiaId, String chave);

    List<NfceImportada> findTop20ByFamiliaIdOrderByImportadaEmDesc(Long familiaId);

    List<NfceImportada> findAllByFamiliaId(Long familiaId);

    /** Última nota do mesmo CNPJ: o nome que a família deu ao mercado. */
    Optional<NfceImportada> findFirstByFamiliaIdAndCnpjOrderByImportadaEmDesc(Long familiaId, String cnpj);
}
