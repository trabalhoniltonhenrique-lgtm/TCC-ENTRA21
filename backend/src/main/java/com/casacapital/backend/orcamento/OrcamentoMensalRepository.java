package com.casacapital.backend.orcamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrcamentoMensalRepository extends JpaRepository<OrcamentoMensal, Long> {

    List<OrcamentoMensal> findAllByFamiliaIdAndMesAno(Long familiaId, String mesAno);

    Optional<OrcamentoMensal> findByFamiliaIdAndMesAnoAndCategoria(Long familiaId, String mesAno, String categoria);

    void deleteAllByFamiliaId(Long familiaId);
}
