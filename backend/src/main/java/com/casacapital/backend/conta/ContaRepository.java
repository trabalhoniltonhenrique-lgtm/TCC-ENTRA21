package com.casacapital.backend.conta;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    List<Conta> findAllByFamiliaIdOrderByVencimentoAsc(Long familiaId);

    Optional<Conta> findByIdAndFamiliaId(Long id, Long familiaId);

    List<Conta> findAllByGrupoParcelaIdAndFamiliaId(Long grupoParcelaId, Long familiaId);

    List<Conta> findAllByFamiliaIdAndParcelaNumAndParcelaTotal(Long familiaId, Integer parcelaNum, Integer parcelaTotal);

    /** A próxima ocorrência de uma conta mensal/anual já foi gerada? (evita gerar de novo ao reabrir e pagar) */
    boolean existsByFamiliaIdAndDescricaoAndVencimentoAndRecorrencia(Long familiaId, String descricao,
                                                                     LocalDate vencimento, Recorrencia recorrencia);
}
