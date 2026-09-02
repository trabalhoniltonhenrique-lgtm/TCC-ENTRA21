package com.casacapital.backend.familia;

import com.casacapital.backend.alerta.AlertaLimiteRepository;
import com.casacapital.backend.compra.CompraRepository;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.dispensa.ItemDispensaRepository;
import com.casacapital.backend.historicoprecos.HistoricoPrecoRepository;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.meta.MetaRepository;
import com.casacapital.backend.orcamento.OrcamentoMensalRepository;
import com.casacapital.backend.receita.ReceitaRepository;
import com.casacapital.backend.tarefa.TarefaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Apaga todos os dados transacionais de uma família, mantendo a família/usuários e o plano. */
@Service
public class FamiliaDataService {

    private final ReceitaRepository receitaRepository;
    private final DespesaRepository despesaRepository;
    private final ContaRepository contaRepository;
    private final CompraRepository compraRepository;
    private final ItemDispensaRepository itemDispensaRepository;
    private final HistoricoPrecoRepository historicoPrecoRepository;
    private final TarefaRepository tarefaRepository;
    private final AlertaLimiteRepository alertaLimiteRepository;
    private final OrcamentoMensalRepository orcamentoMensalRepository;
    private final MetaRepository metaRepository;
    private final MembroFamiliaRepository membroFamiliaRepository;

    public FamiliaDataService(ReceitaRepository receitaRepository, DespesaRepository despesaRepository,
                               ContaRepository contaRepository, CompraRepository compraRepository,
                               ItemDispensaRepository itemDispensaRepository,
                               HistoricoPrecoRepository historicoPrecoRepository, TarefaRepository tarefaRepository,
                               AlertaLimiteRepository alertaLimiteRepository,
                               OrcamentoMensalRepository orcamentoMensalRepository, MetaRepository metaRepository,
                               MembroFamiliaRepository membroFamiliaRepository) {
        this.receitaRepository = receitaRepository;
        this.despesaRepository = despesaRepository;
        this.contaRepository = contaRepository;
        this.compraRepository = compraRepository;
        this.itemDispensaRepository = itemDispensaRepository;
        this.historicoPrecoRepository = historicoPrecoRepository;
        this.tarefaRepository = tarefaRepository;
        this.alertaLimiteRepository = alertaLimiteRepository;
        this.orcamentoMensalRepository = orcamentoMensalRepository;
        this.metaRepository = metaRepository;
        this.membroFamiliaRepository = membroFamiliaRepository;
    }

    @Transactional
    public void apagarDados(Long familiaId) {
        despesaRepository.deleteAll(despesaRepository.findAllByFamiliaIdOrderByDataDesc(familiaId));
        receitaRepository.deleteAll(receitaRepository.findAllByFamiliaIdOrderByDataDesc(familiaId));
        contaRepository.deleteAll(contaRepository.findAllByFamiliaIdOrderByVencimentoAsc(familiaId));
        compraRepository.deleteAll(compraRepository.findAllByFamiliaId(familiaId));
        itemDispensaRepository.deleteAll(itemDispensaRepository.findAllByFamiliaId(familiaId));
        historicoPrecoRepository.deleteAll(historicoPrecoRepository.findAllByFamiliaIdOrderByDataAsc(familiaId));
        tarefaRepository.deleteAll(tarefaRepository.findAllByFamiliaId(familiaId));
        alertaLimiteRepository.deleteAll(alertaLimiteRepository.findAllByFamiliaId(familiaId));
        metaRepository.deleteAll(metaRepository.findAllByFamiliaIdFetchAportes(familiaId));
        membroFamiliaRepository.deleteAll(membroFamiliaRepository.findAllByFamiliaIdOrderByNomeAsc(familiaId));
        // Orçamentos não têm um finder "todos os meses"; removidos mês a mês seria caro,
        // então aqui usamos uma deleção em lote diretamente pela família.
        orcamentoMensalRepository.deleteAllByFamiliaId(familiaId);
    }
}
