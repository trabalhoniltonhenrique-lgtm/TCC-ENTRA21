package com.casacapital.backend.dispensa;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.compra.Compra;
import com.casacapital.backend.compra.CompraRepository;
import com.casacapital.backend.compra.Frequencia;
import com.casacapital.backend.dispensa.dto.ItemDispensaRequest;
import com.casacapital.backend.familia.FamiliaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class DispensaService {

    private final ItemDispensaRepository itemDispensaRepository;
    private final FamiliaRepository familiaRepository;
    private final CompraRepository compraRepository;

    public DispensaService(ItemDispensaRepository itemDispensaRepository, FamiliaRepository familiaRepository,
                            CompraRepository compraRepository) {
        this.itemDispensaRepository = itemDispensaRepository;
        this.familiaRepository = familiaRepository;
        this.compraRepository = compraRepository;
    }

    public List<ItemDispensa> listar(Long familiaId) {
        return itemDispensaRepository.findAllByFamiliaId(familiaId);
    }

    public ItemDispensa buscar(Long familiaId, Long id) {
        return itemDispensaRepository.findByIdAndFamiliaId(id, familiaId)
                .orElseThrow(() -> new ResourceNotFoundException("Item de dispensa não encontrado."));
    }

    /** Adiciona um item novo, ou soma a quantidade se já existir (mesmo nome+categoria). */
    @Transactional
    public ItemDispensa adicionar(Long familiaId, ItemDispensaRequest req) {
        int qtd = req.qtd() != null ? req.qtd() : 1;
        ItemDispensa existente = itemDispensaRepository
                .findByFamiliaIdAndNomeIgnoreCaseAndCategoria(familiaId, req.nome(), req.categoria())
                .orElse(null);

        if (existente != null) {
            existente.setQtd(existente.getQtd() + qtd);
            existente.setDataAtualizacao(LocalDate.now());
            return existente;
        }

        ItemDispensa novo = new ItemDispensa();
        novo.setFamilia(familiaRepository.getReferenceById(familiaId));
        novo.setNome(req.nome());
        novo.setQtd(qtd);
        novo.setCategoria(req.categoria());
        novo.setDataEntrada(LocalDate.now());
        novo.setDataAtualizacao(LocalDate.now());
        return itemDispensaRepository.save(novo);
    }

    @Transactional
    public ItemDispensa atualizarQtd(Long familiaId, Long id, int delta) {
        ItemDispensa item = buscar(familiaId, id);
        item.setQtd(Math.max(0, item.getQtd() + delta));
        item.setDataAtualizacao(LocalDate.now());
        return item;
    }

    @Transactional
    public void excluir(Long familiaId, Long id) {
        itemDispensaRepository.delete(buscar(familiaId, id));
    }

    /** Adiciona (ou incrementa) o item correspondente na lista de compras — não altera a dispensa. */
    @Transactional
    public Compra moverParaCompras(Long familiaId, Long id) {
        ItemDispensa item = buscar(familiaId, id);
        Compra existente = compraRepository.findAllByFamiliaId(familiaId).stream()
                .filter(c -> c.getNome().equalsIgnoreCase(item.getNome()))
                .findFirst()
                .orElse(null);

        if (existente != null) {
            existente.setQtd(existente.getQtd() + 1);
            return existente;
        }

        Compra nova = new Compra();
        nova.setFamilia(familiaRepository.getReferenceById(familiaId));
        nova.setNome(item.getNome());
        nova.setQtd(1);
        nova.setCategoria(item.getCategoria());
        nova.setFrequencia(Frequencia.SEM_FREQUENCIA);
        return compraRepository.save(nova);
    }
}
