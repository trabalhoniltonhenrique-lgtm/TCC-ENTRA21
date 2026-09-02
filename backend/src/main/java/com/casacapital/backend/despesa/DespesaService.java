package com.casacapital.backend.despesa;

import com.casacapital.backend.common.Categorias;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.despesa.dto.DespesaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final FamiliaRepository familiaRepository;
    private final MembroFamiliaRepository membroRepository;

    public DespesaService(DespesaRepository despesaRepository, FamiliaRepository familiaRepository,
                           MembroFamiliaRepository membroRepository) {
        this.despesaRepository = despesaRepository;
        this.familiaRepository = familiaRepository;
        this.membroRepository = membroRepository;
    }

    public List<Despesa> listar(Long familiaId) {
        return despesaRepository.findAllByFamiliaIdOrderByDataDesc(familiaId);
    }

    public Despesa buscar(Long familiaId, Long id) {
        return despesaRepository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Despesa não encontrada."));
    }

    @Transactional
    public Despesa criar(Long familiaId, DespesaRequest req) {
        Categorias.validar(Categorias.DESPESA, req.categoria());
        Despesa d = new Despesa();
        d.setFamilia(familiaRepository.getReferenceById(familiaId));
        aplicar(familiaId, d, req);
        return despesaRepository.save(d);
    }

    @Transactional
    public Despesa atualizar(Long familiaId, Long id, DespesaRequest req) {
        Categorias.validar(Categorias.DESPESA, req.categoria());
        Despesa d = buscar(familiaId, id);
        aplicar(familiaId, d, req);
        return d;
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        despesaRepository.delete(buscar(familiaId, id));
    }

    private void aplicar(Long familiaId, Despesa d, DespesaRequest req) {
        d.setNome(req.nome());
        d.setValor(req.valor());
        d.setCategoria(req.categoria());
        d.setData(req.data());
        if (req.membroId() != null) {
            d.setMembro(membroRepository.findByIdAndFamiliaId(req.membroId(), familiaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado.")));
        } else {
            d.setMembro(null);
        }
    }
}
