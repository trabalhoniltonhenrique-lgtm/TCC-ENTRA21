package com.casacapital.backend.mesada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MesadaRepository extends JpaRepository<Mesada, Long> {

    Optional<Mesada> findByMembroIdAndFamiliaId(Long membroId, Long familiaId);

    List<Mesada> findAllByFamiliaId(Long familiaId);

    List<Mesada> findAllByAtivaTrue();
}
