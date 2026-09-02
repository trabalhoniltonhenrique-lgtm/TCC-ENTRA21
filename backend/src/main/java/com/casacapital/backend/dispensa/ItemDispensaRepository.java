package com.casacapital.backend.dispensa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemDispensaRepository extends JpaRepository<ItemDispensa, Long> {

    List<ItemDispensa> findAllByFamiliaId(Long familiaId);

    Optional<ItemDispensa> findByIdAndFamiliaId(Long id, Long familiaId);

    Optional<ItemDispensa> findByFamiliaIdAndNomeIgnoreCaseAndCategoria(Long familiaId, String nome, String categoria);
}
