package com.casacapital.backend.tarefa;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.tarefa.dto.TarefaRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TarefaService {

    private final TarefaRepository tarefaRepository;
    private final FamiliaRepository familiaRepository;
    private final MembroFamiliaRepository membroRepository;

    public TarefaService(TarefaRepository tarefaRepository, FamiliaRepository familiaRepository,
                          MembroFamiliaRepository membroRepository) {
        this.tarefaRepository = tarefaRepository;
        this.familiaRepository = familiaRepository;
        this.membroRepository = membroRepository;
    }

    public List<Tarefa> listar(Long familiaId) {
        return tarefaRepository.findAllByFamiliaId(familiaId);
    }

    public Tarefa buscar(Long familiaId, Long id) {
        return tarefaRepository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada."));
    }

    @Transactional
    public Tarefa criar(Long familiaId, TarefaRequest req) {
        Tarefa t = new Tarefa();
        t.setFamilia(familiaRepository.getReferenceById(familiaId));
        aplicar(familiaId, t, req);
        return tarefaRepository.save(t);
    }

    @Transactional
    public Tarefa atualizar(Long familiaId, Long id, TarefaRequest req) {
        Tarefa t = buscar(familiaId, id);
        aplicar(familiaId, t, req);
        return t;
    }

    @Transactional
    public Tarefa concluir(Long familiaId, Long id) {
        Tarefa t = buscar(familiaId, id);
        t.setConcluida(!t.isConcluida());
        return t;
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        tarefaRepository.delete(buscar(familiaId, id));
    }

    private void aplicar(Long familiaId, Tarefa t, TarefaRequest req) {
        t.setNome(req.nome());
        t.setPrazo(req.prazo());
        t.setDiaSemana(req.diaSemana());
        if (req.membroId() != null) {
            t.setMembro(membroRepository.findByIdAndFamiliaId(req.membroId(), familiaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado.")));
        } else {
            t.setMembro(null);
        }
    }
}
