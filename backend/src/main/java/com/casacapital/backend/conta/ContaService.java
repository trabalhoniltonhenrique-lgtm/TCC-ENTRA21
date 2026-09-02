package com.casacapital.backend.conta;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.conta.dto.ContaRequest;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ContaService {

    /** Mapeia categoria de conta -> categoria de despesa, espelhando contas.js (marcarPago). */
    private static final Map<String, String> MAPA_CATEGORIA_DESPESA = Map.ofEntries(
            Map.entry("Moradia", "Moradia"),
            Map.entry("Energia", "Contas & Serviços"),
            Map.entry("Água", "Contas & Serviços"),
            Map.entry("Internet / TV", "Contas & Serviços"),
            Map.entry("Telefone", "Contas & Serviços"),
            Map.entry("Cartão de Crédito", "Contas & Serviços"),
            Map.entry("Escola / Faculdade", "Educação"),
            Map.entry("Saúde / Plano", "Saúde"),
            Map.entry("Seguro", "Contas & Serviços"),
            Map.entry("Financiamento", "Moradia"),
            Map.entry("Condomínio", "Moradia"),
            Map.entry("Outros", "Outros")
    );

    private final ContaRepository contaRepository;
    private final DespesaRepository despesaRepository;
    private final FamiliaRepository familiaRepository;

    public ContaService(ContaRepository contaRepository, DespesaRepository despesaRepository,
                         FamiliaRepository familiaRepository) {
        this.contaRepository = contaRepository;
        this.despesaRepository = despesaRepository;
        this.familiaRepository = familiaRepository;
    }

    public List<Conta> listar(Long familiaId) {
        return contaRepository.findAllByFamiliaIdOrderByVencimentoAsc(familiaId);
    }

    public Conta buscar(Long familiaId, Long id) {
        return contaRepository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada."));
    }

    @Transactional
    public List<Conta> criar(Long familiaId, ContaRequest req) {
        Familia familiaRef = familiaRepository.getReferenceById(familiaId);

        if (req.recorrencia() == Recorrencia.PARCELADA) {
            int qtd = req.qtdParcelas() != null ? req.qtdParcelas() : 0;
            if (qtd < 2) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "QTD_PARCELAS_INVALIDA",
                        "Informe um número de parcelas válido (mínimo 2).");
            }
            boolean valorTotal = "total".equalsIgnoreCase(req.tipoValorParcela());
            BigDecimal valorParcela = valorTotal
                    ? req.valor().divide(BigDecimal.valueOf(qtd), 2, RoundingMode.HALF_UP)
                    : req.valor();

            long grupoId = System.currentTimeMillis();
            List<Conta> geradas = new ArrayList<>();
            for (int i = 0; i < qtd; i++) {
                Conta c = new Conta();
                c.setFamilia(familiaRef);
                c.setDescricao(req.descricao() + " (" + (i + 1) + "/" + qtd + ")");
                c.setCategoria(req.categoria());
                c.setValor(valorParcela);
                c.setVencimento(req.vencimento().plusMonths(i));
                c.setRecorrencia(Recorrencia.PARCELADA);
                c.setObs(req.obs());
                c.setGrupoParcelaId(grupoId);
                c.setParcelaNum(i + 1);
                c.setParcelaTotal(qtd);
                geradas.add(contaRepository.save(c));
            }
            return geradas;
        }

        Conta c = new Conta();
        c.setFamilia(familiaRef);
        aplicarCamposBasicos(c, req);
        return List.of(contaRepository.save(c));
    }

    @Transactional
    public Conta atualizar(Long familiaId, Long id, ContaRequest req) {
        // Editar nunca regenera parcelas — apenas atualiza os campos básicos (mesma regra do front-end atual).
        Conta c = buscar(familiaId, id);
        aplicarCamposBasicos(c, req);
        return c;
    }

    private void aplicarCamposBasicos(Conta c, ContaRequest req) {
        c.setDescricao(req.descricao());
        c.setCategoria(req.categoria());
        c.setValor(req.valor());
        c.setVencimento(req.vencimento());
        // Edição nunca regenera parcelas nem muda um item parcelado para outro tipo de recorrência
        // (o front-end trava esse campo ao editar uma parcela; aqui só reforçamos a mesma regra).
        if (c.getGrupoParcelaId() == null) {
            c.setRecorrencia(req.recorrencia());
        }
        c.setObs(req.obs());
    }

    @Transactional
    public Conta marcarPago(Long familiaId, Long id) {
        Conta c = buscar(familiaId, id);
        c.setPago(true);
        c.setDataPagamento(LocalDate.now());

        boolean jaLancada = despesaRepository.existsByOrigemConta_IdAndData(c.getId(), LocalDate.now());
        if (!jaLancada) {
            Despesa d = new Despesa();
            d.setFamilia(familiaRepository.getReferenceById(familiaId));
            d.setNome(c.getDescricao());
            d.setValor(c.getValor());
            d.setCategoria(MAPA_CATEGORIA_DESPESA.getOrDefault(c.getCategoria(), "Outros"));
            d.setData(LocalDate.now());
            d.setOrigemConta(c);
            d.setOrigemContaFlag(true);
            despesaRepository.save(d);
        }

        if (c.getRecorrencia() == Recorrencia.MENSAL || c.getRecorrencia() == Recorrencia.ANUAL) {
            Conta proxima = new Conta();
            proxima.setFamilia(c.getFamilia());
            proxima.setDescricao(c.getDescricao());
            proxima.setCategoria(c.getCategoria());
            proxima.setValor(c.getValor());
            proxima.setVencimento(c.getRecorrencia() == Recorrencia.MENSAL
                    ? c.getVencimento().plusMonths(1)
                    : c.getVencimento().plusYears(1));
            proxima.setRecorrencia(c.getRecorrencia());
            proxima.setObs(c.getObs());
            contaRepository.save(proxima);
        }

        return c;
    }

    @Transactional
    public Conta reabrir(Long familiaId, Long id) {
        Conta c = buscar(familiaId, id);
        despesaRepository.deleteByOrigemConta_Id(c.getId());
        c.setPago(false);
        c.setDataPagamento(null);
        return c;
    }

    @Transactional
    public void excluir(Long familiaId, Long id, boolean apagarGrupoTodo) {
        Conta c = buscar(familiaId, id);

        if (apagarGrupoTodo && c.getGrupoParcelaId() != null) {
            List<Conta> doGrupo = contaRepository.findAllByGrupoParcelaIdAndFamiliaId(c.getGrupoParcelaId(), familiaId);
            List<Conta> futurasNaoPagas = doGrupo.stream()
                    .filter(x -> !x.isPago() && !x.getVencimento().isBefore(c.getVencimento()))
                    .toList();
            contaRepository.deleteAll(futurasNaoPagas);
            return;
        }

        contaRepository.delete(c);
    }
}
