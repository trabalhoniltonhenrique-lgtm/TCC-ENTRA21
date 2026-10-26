package com.casacapital.backend.planejamento;

import com.casacapital.backend.conta.Conta;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.conta.Recorrencia;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.investimento.Investimento;
import com.casacapital.backend.investimento.InvestimentoRepository;
import com.casacapital.backend.planejamento.dto.FluxoCaixaResponse;
import com.casacapital.backend.planejamento.dto.PatrimonioResponse;
import com.casacapital.backend.receita.Receita;
import com.casacapital.backend.receita.ReceitaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlanejamentoService {

    private static final Logger log = LoggerFactory.getLogger(PlanejamentoService.class);
    private static final int MESES_MEDIA = 3;
    private static final int MESES_HISTORICO_PATRIMONIO = 12;

    private final ReceitaRepository receitas;
    private final DespesaRepository despesas;
    private final ContaRepository contas;
    private final InvestimentoRepository investimentos;
    private final PatrimonioMensalRepository fotos;
    private final FamiliaRepository familias;

    public PlanejamentoService(ReceitaRepository receitas, DespesaRepository despesas, ContaRepository contas,
                               InvestimentoRepository investimentos, PatrimonioMensalRepository fotos,
                               FamiliaRepository familias) {
        this.receitas = receitas;
        this.despesas = despesas;
        this.contas = contas;
        this.investimentos = investimentos;
        this.fotos = fotos;
        this.familias = familias;
    }

    // ── Fluxo de caixa dos próximos meses ──

    @Transactional(readOnly = true)
    public FluxoCaixaResponse fluxoCaixa(Long familiaId, int quantidadeMeses, LocalDate hoje) {
        List<Receita> recs = receitas.findAllByFamiliaIdOrderByDataDesc(familiaId);
        List<Despesa> desps = despesas.findAllByFamiliaIdOrderByDataDesc(familiaId);
        List<Conta> todasContas = contas.findAllByFamiliaIdOrderByVencimentoAsc(familiaId);

        BigDecimal saldo = somaReceitas(recs).subtract(somaDespesas(desps));
        YearMonth mesAtual = YearMonth.from(hoje);

        // Médias dos últimos meses completos. Despesas que vieram de contas pagas ficam de fora: as contas
        // futuras já entram pelo cadastro de Contas, e contá-las de novo na média dobraria o valor.
        Map<YearMonth, BigDecimal> recPorMes = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> varPorMes = new LinkedHashMap<>();
        for (int i = 1; i <= MESES_MEDIA; i++) {
            recPorMes.put(mesAtual.minusMonths(i), BigDecimal.ZERO);
            varPorMes.put(mesAtual.minusMonths(i), BigDecimal.ZERO);
        }
        recs.forEach(r -> recPorMes.computeIfPresent(YearMonth.from(r.getData()), (k, v) -> v.add(r.getValor())));
        desps.stream().filter(d -> !d.isOrigemContaFlag())
                .forEach(d -> varPorMes.computeIfPresent(YearMonth.from(d.getData()), (k, v) -> v.add(d.getValor())));
        int mesesComDados = (int) recPorMes.keySet().stream()
                .filter(m -> recPorMes.get(m).signum() != 0 || varPorMes.get(m).signum() != 0).count();
        BigDecimal mediaRec = media(recPorMes.values(), mesesComDados);
        BigDecimal mediaVar = media(varPorMes.values(), mesesComDados);

        BigDecimal recNoMesAtual = recs.stream().filter(r -> YearMonth.from(r.getData()).equals(mesAtual))
                .map(Receita::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal varNoMesAtual = desps.stream().filter(d -> !d.isOrigemContaFlag() && YearMonth.from(d.getData()).equals(mesAtual))
                .map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth ultimoMes = mesAtual.plusMonths(quantidadeMeses - 1L);
        Map<YearMonth, List<FluxoCaixaResponse.Item>> itensPorMes = itensDeContas(todasContas, mesAtual, ultimoMes);

        List<FluxoCaixaResponse.Mes> meses = new ArrayList<>();
        String primeiroNegativo = null;
        for (YearMonth m = mesAtual; !m.isAfter(ultimoMes); m = m.plusMonths(1)) {
            // no mês atual, só o que ainda falta entrar/gastar (o já lançado está no saldo)
            BigDecimal entradas = m.equals(mesAtual) ? mediaRec.subtract(recNoMesAtual).max(BigDecimal.ZERO) : mediaRec;
            BigDecimal variaveis = m.equals(mesAtual) ? mediaVar.subtract(varNoMesAtual).max(BigDecimal.ZERO) : mediaVar;
            List<FluxoCaixaResponse.Item> itens = itensPorMes.getOrDefault(m, List.of());
            BigDecimal contasMes = itens.stream().map(FluxoCaixaResponse.Item::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal parcelas = itens.stream().filter(FluxoCaixaResponse.Item::parcela)
                    .map(FluxoCaixaResponse.Item::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
            saldo = saldo.add(entradas).subtract(contasMes).subtract(variaveis);
            if (primeiroNegativo == null && saldo.signum() < 0) primeiroNegativo = m.toString();
            meses.add(new FluxoCaixaResponse.Mes(m.toString(), dinheiro(entradas), dinheiro(contasMes), dinheiro(parcelas),
                    dinheiro(variaveis), dinheiro(saldo), itens));
        }

        BigDecimal saldoAtual = somaReceitas(recs).subtract(somaDespesas(desps));
        return new FluxoCaixaResponse(dinheiro(saldoAtual), mediaRec, mediaVar, mesesComDados, primeiroNegativo, meses);
    }

    /** Contas em aberto por mês (atrasadas entram no mês atual) + próximas ocorrências das mensais/anuais. */
    private static Map<YearMonth, List<FluxoCaixaResponse.Item>> itensDeContas(List<Conta> todas, YearMonth atual,
                                                                              YearMonth ultimo) {
        Map<YearMonth, List<FluxoCaixaResponse.Item>> porMes = new LinkedHashMap<>();
        for (Conta c : todas) {
            if (c.isPago()) continue;
            YearMonth m = YearMonth.from(c.getVencimento());
            if (m.isBefore(atual)) m = atual;
            if (m.isAfter(ultimo)) continue;
            porMes.computeIfAbsent(m, k -> new ArrayList<>()).add(new FluxoCaixaResponse.Item(c.getDescricao(), c.getValor(),
                    c.getVencimento(), c.getGrupoParcelaId() != null, false));
        }

        // Conta mensal/anual: a próxima ocorrência só é criada quando a atual é paga. Projeta as seguintes a partir
        // da última de cada série (mesma descrição, categoria e recorrência).
        Map<String, Conta> ultimaDaSerie = new LinkedHashMap<>();
        for (Conta c : todas) {
            if (c.getRecorrencia() != Recorrencia.MENSAL && c.getRecorrencia() != Recorrencia.ANUAL) continue;
            String chave = c.getDescricao() + "|" + c.getCategoria() + "|" + c.getRecorrencia();
            Conta u = ultimaDaSerie.get(chave);
            if (u == null || c.getVencimento().isAfter(u.getVencimento())) ultimaDaSerie.put(chave, c);
        }
        for (Conta u : ultimaDaSerie.values()) {
            LocalDate proxima = proxima(u);
            while (!YearMonth.from(proxima).isAfter(ultimo)) {
                if (!YearMonth.from(proxima).isBefore(atual)) {
                    porMes.computeIfAbsent(YearMonth.from(proxima), k -> new ArrayList<>())
                            .add(new FluxoCaixaResponse.Item(u.getDescricao(), u.getValor(), proxima, false, true));
                }
                proxima = u.getRecorrencia() == Recorrencia.MENSAL ? proxima.plusMonths(1) : proxima.plusYears(1);
            }
        }
        porMes.values().forEach(l -> l.sort(Comparator.comparing(FluxoCaixaResponse.Item::vencimento)));
        return porMes;
    }

    private static LocalDate proxima(Conta c) {
        return c.getRecorrencia() == Recorrencia.MENSAL ? c.getVencimento().plusMonths(1) : c.getVencimento().plusYears(1);
    }

    // ── Patrimônio ──

    /** Calcula o patrimônio de hoje, grava a foto do mês e devolve o histórico dos últimos 12 meses. */
    @Transactional
    public PatrimonioResponse patrimonio(Long familiaId, LocalDate hoje) {
        PatrimonioResponse.Foto atual = registrarFoto(familiaId, hoje);
        List<PatrimonioMensal> todas = fotos.findAllByFamiliaIdOrderByMesAsc(familiaId);
        List<PatrimonioResponse.Foto> historico = todas.subList(Math.max(0, todas.size() - MESES_HISTORICO_PATRIMONIO), todas.size())
                .stream().map(p -> new PatrimonioResponse.Foto(p.getMes(), p.getSaldo(), p.getInvestimentos(), p.getDividas(),
                        p.getTotal())).toList();
        return new PatrimonioResponse(atual, historico);
    }

    PatrimonioResponse.Foto registrarFoto(Long familiaId, LocalDate hoje) {
        BigDecimal saldo = somaReceitas(receitas.findAllByFamiliaIdOrderByDataDesc(familiaId))
                .subtract(somaDespesas(despesas.findAllByFamiliaIdOrderByDataDesc(familiaId)));
        BigDecimal investido = investimentos.findAllByFamiliaId(familiaId).stream()
                .map(Investimento::getValorAtual).reduce(BigDecimal.ZERO, BigDecimal::add);
        // dívida = parcelas de compras parceladas ainda não pagas (o que já está comprometido)
        BigDecimal dividas = contas.findAllByFamiliaIdOrderByVencimentoAsc(familiaId).stream()
                .filter(c -> !c.isPago() && c.getGrupoParcelaId() != null)
                .map(Conta::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = saldo.add(investido).subtract(dividas);

        String mes = YearMonth.from(hoje).toString();
        PatrimonioMensal foto = fotos.findByFamiliaIdAndMes(familiaId, mes).orElseGet(() -> {
            PatrimonioMensal p = new PatrimonioMensal();
            p.setFamilia(familias.getReferenceById(familiaId));
            p.setMes(mes);
            return p;
        });
        foto.setSaldo(dinheiro(saldo));
        foto.setInvestimentos(dinheiro(investido));
        foto.setDividas(dinheiro(dividas));
        foto.setTotal(dinheiro(total));
        foto.setAtualizadoEm(hoje);
        fotos.save(foto);
        return new PatrimonioResponse.Foto(mes, foto.getSaldo(), foto.getInvestimentos(), foto.getDividas(), foto.getTotal());
    }

    /** Todo dia de madrugada: atualiza a foto do mês de cada família, para o histórico não ficar com buracos. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void fotografarTodas() {
        for (Familia f : familias.findAll()) {
            try {
                registrarFoto(f.getId(), LocalDate.now());
            } catch (RuntimeException e) {
                log.warn("Não foi possível registrar o patrimônio da família {}: {}", f.getId(), e.getMessage());
            }
        }
    }

    // ── util ──

    private static BigDecimal somaReceitas(List<Receita> l) {
        return l.stream().map(Receita::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal somaDespesas(List<Despesa> l) {
        return l.stream().map(Despesa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal media(java.util.Collection<BigDecimal> valores, int meses) {
        if (meses == 0) return BigDecimal.ZERO.setScale(2);
        return valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(meses), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
