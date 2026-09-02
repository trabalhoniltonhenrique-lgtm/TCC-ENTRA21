package com.casacapital.backend.compra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    List<Compra> findAllByFamiliaId(Long familiaId);

    Optional<Compra> findByIdAndFamiliaId(Long id, Long familiaId);
}
