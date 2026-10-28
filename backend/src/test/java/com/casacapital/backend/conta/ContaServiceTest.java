package com.casacapital.backend.conta;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.conta.dto.ContaRequest;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Contas a pagar: parcelamento, pagamento (despesa + próxima ocorrência), reabrir e excluir. */
class ContaServiceTest {

    private static final long FAMILIA = 1L;

    private ContaRepository contas;
    private DespesaRepository despesas;
    private ContaService service;

    @BeforeEach
    void setUp() {
        contas = mock(ContaRepository.class);
        despesas = mock(DespesaRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(FAMILIA)).thenReturn(new Familia());
        when(contas.save(any(Conta.class))).thenAnswer(i -> i.getArgument(0));
        service = new ContaService(contas, despesas, familias);
    }

    // ── Parcelamento ──

    @Test
    void parceladoPeloTotalNaoPerdeCentavos() {
        List<Conta> parcelas = service.criar(FAMILIA, parcelado("100.00", 3, "total", LocalDate.of(2026, 1, 10)));

        assertThat(parcelas).extracting(Conta::getValor)
                .containsExactly(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34"));
        assertThat(parcelas.stream().map(Conta::getValor).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void parceladoPorParcelaRepeteOValor() {
        List<Conta> parcelas = service.criar(FAMILIA, parcelado("250.00", 4, "parcela", LocalDate.of(2026, 1, 10)));

        assertThat(parcelas).hasSize(4).extracting(Conta::getValor).containsOnly(new BigDecimal("250.00"));
        assertThat(parcelas).extracting(Conta::getDescricao)
                .containsExactly("Geladeira (1/4)", "Geladeira (2/4)", "Geladeira (3/4)", "Geladeira (4/4)");
        assertThat(parcelas).extracting(Conta::getGrupoParcelaId).containsOnly(parcelas.get(0).getGrupoParcelaId());
    }

    @Test
    void parcelasVencemMesAMesSemPerderODiaOriginal() {
        // vence dia 31: fevereiro cai no último dia, mas março volta para 31
        List<Conta> parcelas = service.criar(FAMILIA, parcelado("90.00", 3, "total", LocalDate.of(2026, 1, 31)));

        assertThat(parcelas).extracting(Conta::getVencimento).containsExactly(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31));
    }

    @Test
    void parceladoComMenosDeDuasParcelasEhRecusado() {
        assertThatThrownBy(() -> service.criar(FAMILIA, parcelado("90.00", 1, "total", LocalDate.of(2026, 1, 10))))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("QTD_PARCELAS_INVALIDA");
        verify(contas, never()).save(any());
    }

    @Test
    void editarUmaParcelaNaoMudaARecorrencia() {
        Conta parcela = conta(5L, "Geladeira (2/4)", Recorrencia.PARCELADA, false);
        parcela.setGrupoParcelaId(99L);
        when(contas.findByIdAndFamiliaId(5L, FAMILIA)).thenReturn(Optional.of(parcela));

        service.atualizar(FAMILIA, 5L, new ContaRequest("Geladeira nova (2/4)", "Outros", new BigDecimal("260.00"),
                LocalDate.of(2026, 2, 10), Recorrencia.MENSAL, null, null, null, null));

        assertThat(parcela.getRecorrencia()).isEqualTo(Recorrencia.PARCELADA);
        assertThat(parcela.getValor()).isEqualByComparingTo("260.00");
    }

    // ── Pagamento ──

    @Test
    void pagarLancaADespesaComACategoriaCerta() {
        Conta luz = conta(1L, "Luz Celesc", Recorrencia.UNICA, false);
        luz.setCategoria("Energia");
        when(contas.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(luz));

        service.marcarPago(FAMILIA, 1L);

        ArgumentCaptor<Despesa> despesa = ArgumentCaptor.forClass(Despesa.class);
        verify(despesas).save(despesa.capture());
        assertThat(despesa.getValue().getCategoria()).isEqualTo("Contas & Serviços");
        assertThat(despesa.getValue().getValor()).isEqualByComparingTo("120.00");
        assertThat(despesa.getValue().getOrigemConta()).isSameAs(luz);
        assertThat(luz.isPago()).isTrue();
        verify(contas, never()).save(any()); // conta única não gera a próxima
    }

    @Test
    void pagarMensalGeraAProximaNoMesSeguinteEAnualNoAnoSeguinte() {
        Conta internet = conta(1L, "Internet", Recorrencia.MENSAL, false);
        Conta ipva = conta(2L, "IPVA", Recorrencia.ANUAL, false);
        when(contas.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(internet));
        when(contas.findByIdAndFamiliaId(2L, FAMILIA)).thenReturn(Optional.of(ipva));

        service.marcarPago(FAMILIA, 1L);
        service.marcarPago(FAMILIA, 2L);

        ArgumentCaptor<Conta> proximas = ArgumentCaptor.forClass(Conta.class);
        verify(contas, times(2)).save(proximas.capture());
        assertThat(proximas.getAllValues()).extracting(Conta::getVencimento)
                .containsExactly(LocalDate.of(2026, 11, 20), LocalDate.of(2027, 10, 20));
        assertThat(proximas.getAllValues()).noneMatch(Conta::isPago);
    }

    @Test
    void pagarDeNovoUmaContaJaPagaNaoDuplicaNada() {
        // duplo clique, ou dois aparelhos ao mesmo tempo
        Conta internet = conta(1L, "Internet", Recorrencia.MENSAL, true);
        when(contas.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(internet));

        service.marcarPago(FAMILIA, 1L);

        verify(despesas, never()).save(any());
        verify(contas, never()).save(any());
    }

    @Test
    void reabrirEPagarDeNovoNaoCriaOutraProximaOcorrencia() {
        Conta internet = conta(1L, "Internet", Recorrencia.MENSAL, false);
        when(contas.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(internet));
        when(contas.existsByFamiliaIdAndDescricaoAndVencimentoAndRecorrencia(
                FAMILIA, "Internet", LocalDate.of(2026, 11, 20), Recorrencia.MENSAL))
                .thenReturn(false, true); // na segunda vez, a de novembro já existe

        service.marcarPago(FAMILIA, 1L);
        service.reabrir(FAMILIA, 1L);
        service.marcarPago(FAMILIA, 1L);

        verify(contas, times(1)).save(any());
        verify(despesas).deleteByOrigemConta_Id(1L);
        verify(despesas, times(2)).save(any(Despesa.class)); // a despesa apagada ao reabrir volta ao pagar
    }

    @Test
    void reabrirApagaADespesaEVoltaAFicarEmAberto() {
        Conta luz = conta(1L, "Luz", Recorrencia.UNICA, true);
        luz.setDataPagamento(LocalDate.of(2026, 10, 18));
        when(contas.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(luz));

        service.reabrir(FAMILIA, 1L);

        verify(despesas).deleteByOrigemConta_Id(1L);
        assertThat(luz.isPago()).isFalse();
        assertThat(luz.getDataPagamento()).isNull();
    }

    @Test
    void categoriaDaDespesa() {
        Conta c = conta(1L, "x", Recorrencia.UNICA, false);
        c.setCategoria("Escola / Faculdade");
        assertThat(ContaService.categoriaDespesa(c)).isEqualTo("Educação");
        c.setCategoria("Categoria que não existe");
        assertThat(ContaService.categoriaDespesa(c)).isEqualTo("Outros");
        c.setCategoriaDespesa("Lazer"); // escolhida pela pessoa (ou aprendida na fatura) vale mais
        assertThat(ContaService.categoriaDespesa(c)).isEqualTo("Lazer");
    }

    // ── Excluir ──

    @Test
    void excluirOParceladoApagaSoAsParcelasFuturasEmAberto() {
        Conta p1 = parcela(1L, 1, true), p2 = parcela(2L, 2, false), p3 = parcela(3L, 3, false), p4 = parcela(4L, 4, false);
        when(contas.findByIdAndFamiliaId(2L, FAMILIA)).thenReturn(Optional.of(p2));
        when(contas.findAllByGrupoParcelaIdAndFamiliaId(99L, FAMILIA)).thenReturn(List.of(p1, p2, p3, p4));

        service.excluir(FAMILIA, 2L, true);

        verify(contas).deleteAll(List.of(p2, p3, p4)); // a 1ª, já paga, fica no histórico
    }

    @Test
    void contaDeOutraFamiliaNaoEhEncontrada() {
        when(contas.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.marcarPago(FAMILIA, 1L)).isInstanceOf(ApiException.class);
        verify(despesas, never()).save(any());
    }

    private static ContaRequest parcelado(String valor, int qtd, String tipo, LocalDate vencimento) {
        return new ContaRequest("Geladeira", "Outros", new BigDecimal(valor), vencimento, Recorrencia.PARCELADA,
                null, "Nubank", qtd, tipo);
    }

    private static Conta parcela(Long id, int num, boolean pago) {
        Conta c = conta(id, "Geladeira (" + num + "/4)", Recorrencia.PARCELADA, pago);
        c.setGrupoParcelaId(99L);
        c.setParcelaNum(num);
        c.setParcelaTotal(4);
        c.setVencimento(LocalDate.of(2026, num, 10));
        return c;
    }

    private static Conta conta(Long id, String desc, Recorrencia rec, boolean pago) {
        Conta c = new Conta();
        c.setId(id);
        c.setFamilia(new Familia());
        c.setDescricao(desc);
        c.setCategoria("Outros");
        c.setValor(new BigDecimal("120.00"));
        c.setVencimento(LocalDate.of(2026, 10, 20));
        c.setRecorrencia(rec);
        c.setPago(pago);
        return c;
    }
}
