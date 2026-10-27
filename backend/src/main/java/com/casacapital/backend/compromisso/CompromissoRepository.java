package com.casacapital.backend.compromisso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompromissoRepository extends JpaRepository<Compromisso, Long> {

    List<Compromisso> findAllByFamiliaIdAndInicioGreaterThanEqualOrderByInicioAsc(Long familiaId, LocalDateTime desde);

    /** Candidatos a alerta: ainda não dispensados e com horário numa janela próxima de agora. */
    List<Compromisso> findAllByFamiliaIdAndAvisoDispensadoFalseAndInicioBetween(Long familiaId, LocalDateTime de, LocalDateTime ate);

    Optional<Compromisso> findByIdAndFamiliaId(Long id, Long familiaId);

    List<Compromisso> findAllByFamiliaId(Long familiaId);
}
