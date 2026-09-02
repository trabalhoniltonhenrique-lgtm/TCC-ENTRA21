package com.casacapital.backend.tarefa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TarefaRepository extends JpaRepository<Tarefa, Long> {

    List<Tarefa> findAllByFamiliaId(Long familiaId);

    Optional<Tarefa> findByIdAndFamiliaId(Long id, Long familiaId);
}
