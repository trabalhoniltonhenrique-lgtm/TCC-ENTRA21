package com.casacapital.backend.investimento;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.investimento.dto.InvestimentoRequest;
import com.casacapital.backend.meta.MetaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InvestimentoService {

    /** Espelha a lista do front-end (js/metas.js). */
    public static final Set<String> TIPOS = Set.of(
            "Poupança", "CDB", "LCI / LCA", "Tesouro Direto", "Fundos", "Ações", "FIIs", "ETFs",
            "Previdência", "Criptomoedas", "Outros");

    private final InvestimentoRepository repository;
    private final FamiliaRepository familiaRepository;
    private final MetaRepository metaRepository;

    public InvestimentoService(InvestimentoRepository repository, FamiliaRepository familiaRepository,
                               MetaRepository metaRepository) {
        this.repository = repository;
        this.familiaRepository = familiaRepository;
        this.metaRepository = metaRepository;
    }

    @Transactional(readOnly = true)
    public List<Investimento> listar(Long familiaId) {
        return repository.findAllByFamiliaId(familiaId);
    }

    /** Soma do valor atual dos investimentos ligados a cada meta: { metaId: total }. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> totalPorMeta(Long familiaId) {
        Map<Long, BigDecimal> total = new HashMap<>();
        for (Investimento i : repository.findAllByFamiliaId(familiaId)) {
            if (i.getMeta() != null) total.merge(i.getMeta().getId(), i.getValorAtual(), BigDecimal::add);
        }
        return total;
    }

    @Transactional
    public Investimento criar(Long familiaId, InvestimentoRequest req) {
        Investimento i = new Investimento();
        i.setFamilia(familiaRepository.getReferenceById(familiaId));
        aplicar(familiaId, i, req);
        return repository.save(i);
    }

    @Transactional
    public Investimento atualizar(Long familiaId, Long id, InvestimentoRequest req) {
        Investimento i = buscar(familiaId, id);
        aplicar(familiaId, i, req);
        return i;
    }

    @Transactional
    public Investimento atualizarValor(Long familiaId, Long id, BigDecimal valorAtual) {
        Investimento i = buscar(familiaId, id);
        i.setValorAtual(valorAtual);
        i.setAtualizadoEm(LocalDate.now());
        return i;
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        repository.delete(buscar(familiaId, id));
    }

    private Investimento buscar(Long familiaId, Long id) {
        return repository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Investimento não encontrado."));
    }

    private void aplicar(Long familiaId, Investimento i, InvestimentoRequest req) {
        if (!TIPOS.contains(req.tipo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIPO_INVALIDO", "Tipo de investimento inválido: " + req.tipo());
        }
        if (i.getValorAtual() == null || i.getValorAtual().compareTo(req.valorAtual()) != 0) {
            i.setAtualizadoEm(LocalDate.now());
        }
        i.setNome(req.nome().trim());
        i.setTipo(req.tipo());
        i.setInstituicao(vazioParaNull(req.instituicao()));
        i.setValorAplicado(req.valorAplicado());
        i.setValorAtual(req.valorAtual());
        i.setDataAplicacao(req.dataAplicacao());
        i.setVencimento(req.vencimento());
        i.setObs(vazioParaNull(req.obs()));
        i.setMeta(req.metaId() == null ? null : metaRepository.findByIdAndFamiliaId(req.metaId(), familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada.")));
    }

    private static String vazioParaNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
