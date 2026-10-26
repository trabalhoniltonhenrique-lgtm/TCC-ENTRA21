package com.casacapital.backend.mesada;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MesadaMovimentoRepository extends JpaRepository<MesadaMovimento, Long> {

    List<MesadaMovimento> findAllByMembroIdAndFamiliaIdOrderByDataDescIdDesc(Long membroId, Long familiaId);

    Optional<MesadaMovimento> findByIdAndMembroIdAndFamiliaId(Long id, Long membroId, Long familiaId);

    boolean existsByMembroIdAndReferencia(Long membroId, String referencia);
}
