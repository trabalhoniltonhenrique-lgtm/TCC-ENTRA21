package com.casacapital.backend.fechamento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FechamentoEnviadoRepository extends JpaRepository<FechamentoEnviado, Long> {

    boolean existsByFamiliaIdAndMes(Long familiaId, String mes);
}
