package com.casacapital.backend.planejamento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatrimonioMensalRepository extends JpaRepository<PatrimonioMensal, Long> {

    List<PatrimonioMensal> findAllByFamiliaIdOrderByMesAsc(Long familiaId);

    Optional<PatrimonioMensal> findByFamiliaIdAndMes(Long familiaId, String mes);
}
