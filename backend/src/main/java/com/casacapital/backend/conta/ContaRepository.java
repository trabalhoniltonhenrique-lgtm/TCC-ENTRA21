package com.casacapital.backend.conta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    List<Conta> findAllByFamiliaIdOrderByVencimentoAsc(Long familiaId);

    Optional<Conta> findByIdAndFamiliaId(Long id, Long familiaId);

    List<Conta> findAllByGrupoParcelaIdAndFamiliaId(Long grupoParcelaId, Long familiaId);
}
