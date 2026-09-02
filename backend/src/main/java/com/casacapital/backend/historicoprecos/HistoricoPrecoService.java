package com.casacapital.backend.historicoprecos;

import com.casacapital.backend.compra.CompraRepository;
import com.casacapital.backend.dispensa.ItemDispensaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.historicoprecos.dto.HistoricoPontoResponse;
import com.casacapital.backend.historicoprecos.dto.ProdutoHistoricoResponse;
import com.casacapital.backend.historicoprecos.dto.VariacaoResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoricoPrecoService {

    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final ItemDispensaRepository itemDispensaRepository;
    private final CompraRepository compraRepository;

    public HistoricoPrecoService(HistoricoPrecoRepository historicoPrecoRepository,
                                  ItemDispensaRepository itemDispensaRepository,
                                  CompraRepository compraRepository) {
        this.historicoPrecoRepository = historicoPrecoRepository;
        this.itemDispensaRepository = itemDispensaRepository;
        this.compraRepository = compraRepository;
    }

    @Transactional
    public void registrar(Familia familia, String nomeProduto, BigDecimal precoUnitario) {
        if (nomeProduto == null || precoUnitario == null || precoUnitario.signum() <= 0) return;
        historicoPrecoRepository.save(new HistoricoPreco(
                familia, HistoricoPreco.chave(nomeProduto), java.time.LocalDate.now(), precoUnitario));
    }

    @Transactional(readOnly = true)
    public List<ProdutoHistoricoResponse> listar(Long familiaId) {
        List<HistoricoPreco> todos = historicoPrecoRepository.findAllByFamiliaIdOrderByDataAsc(familiaId);

        Map<String, List<HistoricoPreco>> porProduto = new LinkedHashMap<>();
        for (HistoricoPreco h : todos) {
            porProduto.computeIfAbsent(h.getProdutoChave(), k -> new java.util.ArrayList<>()).add(h);
        }

        return porProduto.entrySet().stream()
                .map(e -> montarResposta(familiaId, e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ProdutoHistoricoResponse::nome))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProdutoHistoricoResponse buscar(Long familiaId, String produtoChave) {
        List<HistoricoPreco> registros = historicoPrecoRepository
                .findAllByFamiliaIdAndProdutoChaveOrderByDataAsc(familiaId, produtoChave);
        return montarResposta(familiaId, produtoChave, registros);
    }

    private ProdutoHistoricoResponse montarResposta(Long familiaId, String chave, List<HistoricoPreco> registros) {
        List<HistoricoPontoResponse> pontos = registros.stream()
                .map(r -> new HistoricoPontoResponse(r.getData(), r.getPreco()))
                .toList();

        HistoricoPreco ultimo = registros.get(registros.size() - 1);
        VariacaoResponse variacao = null;
        if (registros.size() >= 2) {
            BigDecimal atual = ultimo.getPreco();
            BigDecimal anterior = registros.get(registros.size() - 2).getPreco();
            if (anterior.signum() > 0) {
                double pct = atual.subtract(anterior)
                        .divide(anterior, 6, RoundingMode.HALF_UP)
                        .doubleValue() * 100;
                variacao = new VariacaoResponse(atual, anterior, pct, pct > 0.05, pct < -0.05);
            }
        }

        String nome = nomeExibicao(familiaId, chave);
        return new ProdutoHistoricoResponse(chave, nome, pontos, ultimo.getPreco(), ultimo.getData(), variacao);
    }

    private String nomeExibicao(Long familiaId, String chave) {
        return itemDispensaRepository.findAllByFamiliaId(familiaId).stream()
                .filter(d -> d.getNome().equalsIgnoreCase(chave))
                .map(d -> d.getNome())
                .findFirst()
                .or(() -> compraRepository.findAllByFamiliaId(familiaId).stream()
                        .filter(c -> c.getNome().equalsIgnoreCase(chave))
                        .map(c -> c.getNome())
                        .findFirst())
                .orElseGet(() -> chave.isEmpty() ? chave : Character.toUpperCase(chave.charAt(0)) + chave.substring(1));
    }
}
