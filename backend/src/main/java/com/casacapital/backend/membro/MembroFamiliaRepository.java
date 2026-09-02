package com.casacapital.backend.membro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembroFamiliaRepository extends JpaRepository<MembroFamilia, Long> {

    List<MembroFamilia> findAllByFamiliaIdOrderByNomeAsc(Long familiaId);

    Optional<MembroFamilia> findByIdAndFamiliaId(Long id, Long familiaId);
}
