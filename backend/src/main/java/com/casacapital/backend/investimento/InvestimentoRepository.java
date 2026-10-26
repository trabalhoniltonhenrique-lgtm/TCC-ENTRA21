package com.casacapital.backend.investimento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvestimentoRepository extends JpaRepository<Investimento, Long> {

    // JOIN FETCH da meta: o DTO lê o nome dela fora da transação (open-in-view=false)
    @Query("select i from Investimento i left join fetch i.meta where i.familia.id = :familiaId order by i.tipo, i.nome")
    List<Investimento> findAllByFamiliaId(@Param("familiaId") Long familiaId);

    @Query("select i from Investimento i left join fetch i.meta where i.id = :id and i.familia.id = :familiaId")
    Optional<Investimento> findByIdAndFamiliaId(@Param("id") Long id, @Param("familiaId") Long familiaId);
}
