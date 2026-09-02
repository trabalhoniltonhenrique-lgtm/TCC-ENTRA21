package com.casacapital.backend.membro;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.dto.MembroRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MembroFamiliaService {

    private final MembroFamiliaRepository membroRepository;
    private final FamiliaRepository familiaRepository;

    public MembroFamiliaService(MembroFamiliaRepository membroRepository, FamiliaRepository familiaRepository) {
        this.membroRepository = membroRepository;
        this.familiaRepository = familiaRepository;
    }

    public List<MembroFamilia> listar(Long familiaId) {
        return membroRepository.findAllByFamiliaIdOrderByNomeAsc(familiaId);
    }

    public MembroFamilia buscar(Long familiaId, Long id) {
        return membroRepository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
    }

    @Transactional
    public MembroFamilia criar(Long familiaId, MembroRequest req) {
        MembroFamilia m = new MembroFamilia();
        m.setFamilia(familiaRepository.getReferenceById(familiaId));
        aplicar(m, req);
        return membroRepository.save(m);
    }

    @Transactional
    public MembroFamilia atualizar(Long familiaId, Long id, MembroRequest req) {
        MembroFamilia m = buscar(familiaId, id);
        aplicar(m, req);
        return m;
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        MembroFamilia m = buscar(familiaId, id);
        membroRepository.delete(m);
    }

    private void aplicar(MembroFamilia m, MembroRequest req) {
        m.setNome(req.nome());
        m.setParentesco(req.parentesco());
        m.setNascimento(req.nascimento());
        m.setRenda(req.renda());
        m.setObs(req.obs());
        m.setCor(req.cor());
    }
}
