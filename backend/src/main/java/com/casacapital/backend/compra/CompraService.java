package com.casacapital.backend.compra;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.compra.dto.CompraRequest;
import com.casacapital.backend.dispensa.ItemDispensa;
import com.casacapital.backend.dispensa.ItemDispensaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.historicoprecos.HistoricoPrecoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final ItemDispensaRepository itemDispensaRepository;
    private final FamiliaRepository familiaRepository;
    private final HistoricoPrecoService historicoPrecoService;

    public CompraService(CompraRepository compraRepository, ItemDispensaRepository itemDispensaRepository,
                          FamiliaRepository familiaRepository, HistoricoPrecoService historicoPrecoService) {
        this.compraRepository = compraRepository;
        this.itemDispensaRepository = itemDispensaRepository;
        this.familiaRepository = familiaRepository;
        this.historicoPrecoService = historicoPrecoService;
    }

    public List<Compra> listar(Long familiaId) {
        return compraRepository.findAllByFamiliaId(familiaId);
    }

    public Compra buscar(Long familiaId, Long id) {
        return compraRepository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Item de compra não encontrado."));
    }

    @Transactional
    public Compra criar(Long familiaId, CompraRequest req) {
        Compra c = new Compra();
        c.setFamilia(familiaRepository.getReferenceById(familiaId));
        aplicar(c, req);
        return compraRepository.save(c);
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        compraRepository.delete(buscar(familiaId, id));
    }

    /** Marca o item como comprado: registra preço (opcional), move para a dispensa e remove da lista. */
    @Transactional
    public ItemDispensa comprarItem(Long familiaId, Long id, BigDecimal precoUnitario) {
        Compra item = buscar(familiaId, id);
        Familia familia = familiaRepository.getReferenceById(familiaId);

        if (precoUnitario != null && precoUnitario.signum() > 0) {
            historicoPrecoService.registrar(familia, item.getNome(), precoUnitario);
        }

        ItemDispensa destino = itemDispensaRepository
                .findByFamiliaIdAndNomeIgnoreCaseAndCategoria(familiaId, item.getNome(), item.getCategoria())
                .orElse(null);

        if (destino != null) {
            destino.setQtd(destino.getQtd() + item.getQtd());
            destino.setDataAtualizacao(LocalDate.now());
        } else {
            destino = new ItemDispensa();
            destino.setFamilia(familia);
            destino.setNome(item.getNome());
            destino.setQtd(item.getQtd());
            destino.setCategoria(item.getCategoria());
            destino.setDataEntrada(LocalDate.now());
            destino.setDataAtualizacao(LocalDate.now());
            itemDispensaRepository.save(destino);
        }

        compraRepository.delete(item);
        return destino;
    }

    private void aplicar(Compra c, CompraRequest req) {
        c.setNome(req.nome());
        c.setQtd(req.qtd() != null ? req.qtd() : 1);
        c.setCategoria(req.categoria());
        c.setFrequencia(req.frequencia());
    }
}
