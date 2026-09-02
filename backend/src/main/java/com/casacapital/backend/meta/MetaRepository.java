package com.casacapital.backend.meta;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MetaRepository extends JpaRepository<Meta, Long> {

    // Usa JOIN FETCH para carregar os aportes na mesma consulta — evita LazyInitializationException
    // ao montar o DTO fora da transação (open-in-view=false).
    @Query("select distinct m from Meta m left join fetch m.aportes where m.familia.id = :familiaId order by m.id")
    List<Meta> findAllByFamiliaIdFetchAportes(@Param("familiaId") Long familiaId);

    @Query("select m from Meta m left join fetch m.aportes where m.id = :id and m.familia.id = :familiaId")
    Optional<Meta> findByIdAndFamiliaIdFetchAportes(@Param("id") Long id, @Param("familiaId") Long familiaId);

    Optional<Meta> findByIdAndFamiliaId(Long id, Long familiaId);
}
