package com.casacapital.backend.receita;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceitaRepository extends JpaRepository<Receita, Long> {

    List<Receita> findAllByFamiliaIdOrderByDataDesc(Long familiaId);

    Optional<Receita> findByIdAndFamiliaId(Long id, Long familiaId);
}
