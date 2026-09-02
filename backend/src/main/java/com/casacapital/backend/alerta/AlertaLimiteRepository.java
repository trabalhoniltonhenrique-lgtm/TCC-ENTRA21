package com.casacapital.backend.alerta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertaLimiteRepository extends JpaRepository<AlertaLimite, Long> {

    List<AlertaLimite> findAllByFamiliaId(Long familiaId);

    Optional<AlertaLimite> findByFamiliaIdAndCategoria(Long familiaId, String categoria);
}
