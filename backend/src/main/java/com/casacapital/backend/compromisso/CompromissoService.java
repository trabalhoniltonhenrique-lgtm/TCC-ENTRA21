package com.casacapital.backend.compromisso;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.compromisso.dto.CompromissoRequest;
import com.casacapital.backend.compromisso.dto.CompromissoResponse;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Compromissos agendados. O alerta aparece a partir de (horário menos a antecedência) e continua aparecendo
 * até alguém clicar "Ciente" ou até uma hora depois do horário. "Lembrar depois" adia o alerta.
 * membroDoFilho != null limita tudo aos compromissos daquele membro (login de filho).
 */
@Service
public class CompromissoService {

    static final int ALERTA_ATE_MINUTOS_DEPOIS = 60;
    private static final int HISTORICO_DIAS = 30;

    private final CompromissoRepository repo;
    private final FamiliaRepository familias;
    private final MembroFamiliaRepository membros;

    public CompromissoService(CompromissoRepository repo, FamiliaRepository familias, MembroFamiliaRepository membros) {
        this.repo = repo;
        this.familias = familias;
        this.membros = membros;
    }

    /** Dos últimos 30 dias em diante, em ordem de horário. */
    @Transactional(readOnly = true)
    public List<CompromissoResponse> listar(Long familiaId, Long membroDoFilho) {
        return repo.findAllByFamiliaIdAndInicioGreaterThanEqualOrderByInicioAsc(familiaId,
                        LocalDateTime.now().minusDays(HISTORICO_DIAS)).stream()
                .filter(c -> visivel(c, membroDoFilho))
                .map(CompromissoResponse::from).toList();
    }

    @Transactional
    public CompromissoResponse criar(Long familiaId, CompromissoRequest req) {
        Compromisso c = new Compromisso();
        c.setFamilia(familias.getReferenceById(familiaId));
        aplicar(familiaId, c, req);
        return CompromissoResponse.from(repo.save(c));
    }

    @Transactional
    public CompromissoResponse atualizar(Long familiaId, Long id, CompromissoRequest req) {
        Compromisso c = buscar(familiaId, id, null);
        aplicar(familiaId, c, req);
        // horário ou antecedência podem ter mudado: o alerta vale de novo
        c.setAvisoDispensado(false);
        c.setAdiadoAte(null);
        return CompromissoResponse.from(c);
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        repo.delete(buscar(familiaId, id, null));
    }

    /** Compromissos cujo alerta deve estar aparecendo agora. */
    @Transactional(readOnly = true)
    public List<CompromissoResponse> alertas(Long familiaId, Long membroDoFilho) {
        return alertas(familiaId, membroDoFilho, LocalDateTime.now());
    }

    List<CompromissoResponse> alertas(Long familiaId, Long membroDoFilho, LocalDateTime agora) {
        // antecedência máxima é de 7 dias: busca até 8 dias à frente
        return repo.findAllByFamiliaIdAndAvisoDispensadoFalseAndInicioBetween(familiaId,
                        agora.minusMinutes(ALERTA_ATE_MINUTOS_DEPOIS), agora.plusDays(8)).stream()
                .filter(c -> visivel(c, membroDoFilho))
                .filter(c -> !c.momentoDoAviso().isAfter(agora))
                .sorted(Comparator.comparing(Compromisso::getInicio))
                .map(CompromissoResponse::from).toList();
    }

    @Transactional
    public void ciente(Long familiaId, Long id, Long membroDoFilho) {
        buscar(familiaId, id, membroDoFilho).setAvisoDispensado(true);
    }

    @Transactional
    public void adiar(Long familiaId, Long id, int minutos, Long membroDoFilho) {
        buscar(familiaId, id, membroDoFilho)
                .setAdiadoAte(LocalDateTime.now().plusMinutes(Math.max(1, Math.min(minutos, 1440))));
    }

    private static boolean visivel(Compromisso c, Long membroDoFilho) {
        return membroDoFilho == null || (c.getMembro() != null && c.getMembro().getId().equals(membroDoFilho));
    }

    private Compromisso buscar(Long familiaId, Long id, Long membroDoFilho) {
        return repo.findByIdAndFamiliaId(id, familiaId)
                .filter(c -> visivel(c, membroDoFilho))
                .orElseThrow(() -> new ResourceNotFoundException("Compromisso não encontrado."));
    }

    private void aplicar(Long familiaId, Compromisso c, CompromissoRequest req) {
        c.setTitulo(req.titulo().trim());
        c.setInicio(req.inicio().withSecond(0).withNano(0));
        c.setLocal(req.local() != null && !req.local().isBlank() ? req.local().trim() : null);
        c.setObs(req.obs() != null && !req.obs().isBlank() ? req.obs().trim() : null);
        c.setAntecedenciaMin(req.antecedenciaMin() != null ? req.antecedenciaMin() : 15);
        c.setMembro(req.membroId() == null ? null : membros.findByIdAndFamiliaId(req.membroId(), familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado.")));
    }
}
