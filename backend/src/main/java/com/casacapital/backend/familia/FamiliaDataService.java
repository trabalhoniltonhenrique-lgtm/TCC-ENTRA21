package com.casacapital.backend.familia;

import com.casacapital.backend.alerta.AlertaLimiteRepository;
import com.casacapital.backend.assinatura.AssinaturaIgnoradaRepository;
import com.casacapital.backend.compra.CompraRepository;
import com.casacapital.backend.compromisso.CompromissoRepository;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.dispensa.ItemDispensaRepository;
import com.casacapital.backend.fatura.CategoriaAprendidaRepository;
import com.casacapital.backend.historicoprecos.HistoricoPrecoRepository;
import com.casacapital.backend.investimento.InvestimentoRepository;
import com.casacapital.backend.membro.MembroFamiliaRepository;
import com.casacapital.backend.meta.MetaRepository;
import com.casacapital.backend.nfce.NfceImportadaRepository;
import com.casacapital.backend.orcamento.OrcamentoMensalRepository;
import com.casacapital.backend.planejamento.PatrimonioMensalRepository;
import com.casacapital.backend.receita.ReceitaRepository;
import com.casacapital.backend.tarefa.TarefaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga todos os dados de uma família (lançamentos, contas, listas, metas, investimentos, histórico de patrimônio
 * e o que o sistema aprendeu nas faturas), mantendo a família, os logins dos pais, o plano, as configurações e a foto.
 */
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
    private final NfceImportadaRepository nfceImportadaRepository;
    private final CompromissoRepository compromissoRepository;
    private final InvestimentoRepository investimentoRepository;
    private final PatrimonioMensalRepository patrimonioMensalRepository;
    private final AssinaturaIgnoradaRepository assinaturaIgnoradaRepository;
    private final CategoriaAprendidaRepository categoriaAprendidaRepository;

    public FamiliaDataService(ReceitaRepository receitaRepository, DespesaRepository despesaRepository,
                               ContaRepository contaRepository, CompraRepository compraRepository,
                               ItemDispensaRepository itemDispensaRepository,
                               HistoricoPrecoRepository historicoPrecoRepository, TarefaRepository tarefaRepository,
                               AlertaLimiteRepository alertaLimiteRepository,
                               OrcamentoMensalRepository orcamentoMensalRepository, MetaRepository metaRepository,
                               MembroFamiliaRepository membroFamiliaRepository,
                               NfceImportadaRepository nfceImportadaRepository,
                               CompromissoRepository compromissoRepository,
                               InvestimentoRepository investimentoRepository,
                               PatrimonioMensalRepository patrimonioMensalRepository,
                               AssinaturaIgnoradaRepository assinaturaIgnoradaRepository,
                               CategoriaAprendidaRepository categoriaAprendidaRepository) {
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
        this.nfceImportadaRepository = nfceImportadaRepository;
        this.compromissoRepository = compromissoRepository;
        this.investimentoRepository = investimentoRepository;
        this.patrimonioMensalRepository = patrimonioMensalRepository;
        this.assinaturaIgnoradaRepository = assinaturaIgnoradaRepository;
        this.categoriaAprendidaRepository = categoriaAprendidaRepository;
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
        compromissoRepository.deleteAll(compromissoRepository.findAllByFamiliaId(familiaId));
        alertaLimiteRepository.deleteAll(alertaLimiteRepository.findAllByFamiliaId(familiaId));
        // investimentos antes das metas: um investimento pode estar ligado a uma meta
        investimentoRepository.deleteAll(investimentoRepository.findAllByFamiliaId(familiaId));
        metaRepository.deleteAll(metaRepository.findAllByFamiliaIdFetchAportes(familiaId));
        patrimonioMensalRepository.deleteAll(patrimonioMensalRepository.findAllByFamiliaIdOrderByMesAsc(familiaId));
        assinaturaIgnoradaRepository.deleteAll(assinaturaIgnoradaRepository.findAllByFamiliaId(familiaId));
        categoriaAprendidaRepository.deleteAll(categoriaAprendidaRepository.findAllByFamiliaId(familiaId));
        // apagar o membro apaga, pelo banco, o login de filho, a mesada e o extrato dele
        membroFamiliaRepository.deleteAll(membroFamiliaRepository.findAllByFamiliaIdOrderByNomeAsc(familiaId));
        nfceImportadaRepository.deleteAll(nfceImportadaRepository.findAllByFamiliaId(familiaId));
        // Orçamentos não têm um finder "todos os meses"; removidos mês a mês seria caro,
        // então aqui usamos uma deleção em lote diretamente pela família.
        orcamentoMensalRepository.deleteAllByFamiliaId(familiaId);
    }
}
