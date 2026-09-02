package com.casacapital.backend.despesa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DespesaRepository extends JpaRepository<Despesa, Long> {

    List<Despesa> findAllByFamiliaIdOrderByDataDesc(Long familiaId);

    Optional<Despesa> findByIdAndFamiliaId(Long id, Long familiaId);

    boolean existsByOrigemConta_IdAndData(Long contaId, LocalDate data);

    void deleteByOrigemConta_Id(Long contaId);
}
