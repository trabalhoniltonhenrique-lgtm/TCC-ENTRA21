package com.casacapital.backend.assinatura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssinaturaIgnoradaRepository extends JpaRepository<AssinaturaIgnorada, Long> {

    List<AssinaturaIgnorada> findAllByFamiliaId(Long familiaId);

    Optional<AssinaturaIgnorada> findByFamiliaIdAndChave(Long familiaId, String chave);
}
