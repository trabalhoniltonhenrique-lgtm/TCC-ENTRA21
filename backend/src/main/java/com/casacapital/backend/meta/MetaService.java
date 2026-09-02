package com.casacapital.backend.meta;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.meta.dto.MetaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class MetaService {

    private final MetaRepository metaRepository;
    private final FamiliaRepository familiaRepository;

    public MetaService(MetaRepository metaRepository, FamiliaRepository familiaRepository) {
        this.metaRepository = metaRepository;
        this.familiaRepository = familiaRepository;
    }

    @Transactional(readOnly = true)
    public List<Meta> listar(Long familiaId) {
        return metaRepository.findAllByFamiliaIdFetchAportes(familiaId);
    }

    @Transactional(readOnly = true)
    public Meta buscar(Long familiaId, Long id) {
        return metaRepository.findByIdAndFamiliaIdFetchAportes(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada."));
    }

    @Transactional
    public Meta criar(Long familiaId, MetaRequest req) {
        Meta m = new Meta();
        m.setFamilia(familiaRepository.getReferenceById(familiaId));
        m.setNome(req.nome());
        m.setValorAlvo(req.valorAlvo());
        m.setPrazo(req.prazo());
        return metaRepository.save(m);
    }

    @Transactional
    public Meta atualizar(Long familiaId, Long id, MetaRequest req) {
        Meta m = buscar(familiaId, id);
        m.setNome(req.nome());
        m.setValorAlvo(req.valorAlvo());
        m.setPrazo(req.prazo());
        return m;
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        metaRepository.delete(buscar(familiaId, id));
    }

    @Transactional
    public Meta aportar(Long familiaId, Long id, BigDecimal valor) {
        Meta m = buscar(familiaId, id);
        m.getAportes().add(new MetaAporte(m, valor, LocalDate.now()));
        if (m.totalPoupado().compareTo(m.getValorAlvo()) >= 0) {
            m.setConcluida(true);
        }
        return m;
    }
}
