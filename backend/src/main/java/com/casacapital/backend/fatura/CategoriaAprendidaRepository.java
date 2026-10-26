package com.casacapital.backend.fatura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaAprendidaRepository extends JpaRepository<CategoriaAprendida, Long> {

    List<CategoriaAprendida> findAllByFamiliaId(Long familiaId);
}
