package com.casacapital.backend.receita;

import com.casacapital.backend.common.Categorias;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.receita.dto.ReceitaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReceitaService {

    private final ReceitaRepository receitaRepository;
    private final FamiliaRepository familiaRepository;
    private final MembroFamiliaRepository membroRepository;

    public ReceitaService(ReceitaRepository receitaRepository, FamiliaRepository familiaRepository,
                           MembroFamiliaRepository membroRepository) {
        this.receitaRepository = receitaRepository;
        this.familiaRepository = familiaRepository;
        this.membroRepository = membroRepository;
    }

    public List<Receita> listar(Long familiaId) {
        return receitaRepository.findAllByFamiliaIdOrderByDataDesc(familiaId);
    }

    public Receita buscar(Long familiaId, Long id) {
        return receitaRepository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Receita não encontrada."));
    }

    @Transactional
    public Receita criar(Long familiaId, ReceitaRequest req) {
        Categorias.validar(Categorias.RECEITA, req.categoria());
        Receita r = new Receita();
        r.setFamilia(familiaRepository.getReferenceById(familiaId));
        aplicar(familiaId, r, req);
        return receitaRepository.save(r);
    }

    @Transactional
    public Receita atualizar(Long familiaId, Long id, ReceitaRequest req) {
        Categorias.validar(Categorias.RECEITA, req.categoria());
        Receita r = buscar(familiaId, id);
        aplicar(familiaId, r, req);
        return r;
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        receitaRepository.delete(buscar(familiaId, id));
    }

    private void aplicar(Long familiaId, Receita r, ReceitaRequest req) {
        r.setNome(req.nome());
        r.setValor(req.valor());
        r.setCategoria(req.categoria());
        r.setData(req.data());
        if (req.membroId() != null) {
            r.setMembro(membroRepository.findByIdAndFamiliaId(req.membroId(), familiaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado.")));
        } else {
            r.setMembro(null);
        }
    }
}
