package com.casacapital.backend.historicoprecos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoricoPrecoRepository extends JpaRepository<HistoricoPreco, Long> {

    List<HistoricoPreco> findAllByFamiliaIdOrderByDataAsc(Long familiaId);

    List<HistoricoPreco> findAllByFamiliaIdAndProdutoChaveOrderByDataAsc(Long familiaId, String produtoChave);
}
