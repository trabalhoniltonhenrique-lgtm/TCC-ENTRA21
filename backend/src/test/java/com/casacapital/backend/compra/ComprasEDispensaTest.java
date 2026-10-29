package com.casacapital.backend.compra;

import com.casacapital.backend.common.ResourceNotFoundException;
import com.casacapital.backend.compra.dto.CompraRequest;
import com.casacapital.backend.dispensa.DispensaService;
import com.casacapital.backend.dispensa.ItemDispensa;
import com.casacapital.backend.dispensa.ItemDispensaRepository;
import com.casacapital.backend.dispensa.dto.ItemDispensaRequest;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.historicoprecos.HistoricoPrecoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lista de compras e despensa: comprar leva para a despensa (somando), preço vai para o histórico. */
class ComprasEDispensaTest {

    private static final long FAMILIA = 1L, OUTRA = 2L;

    private CompraRepository compras;
    private ItemDispensaRepository despensa;
    private HistoricoPrecoService precos;
    private CompraService compraService;
    private DispensaService dispensaService;
    private final Familia familia = new Familia("Família Teste");

    @BeforeEach
    void setUp() {
        compras = mock(CompraRepository.class);
        despensa = mock(ItemDispensaRepository.class);
        precos = mock(HistoricoPrecoService.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(FAMILIA)).thenReturn(familia);
        when(compras.save(any(Compra.class))).thenAnswer(i -> i.getArgument(0));
        when(despensa.save(any(ItemDispensa.class))).thenAnswer(i -> i.getArgument(0));
        when(despensa.findByFamiliaIdAndNomeIgnoreCaseAndCategoria(eq(FAMILIA), anyString(), anyString())).thenReturn(Optional.empty());
        compraService = new CompraService(compras, despensa, familias, precos);
        dispensaService = new DispensaService(despensa, familias, compras);
    }

    @Test
    void comprarComPrecoRegistraOPrecoLevaParaADespensaETiraDaLista() {
        Compra arroz = compra(1L, "Arroz 5kg", 2);
        when(compras.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(arroz));

        ItemDispensa naDespensa = compraService.comprarItem(FAMILIA, 1L, new BigDecimal("27.50"));

        verify(precos).registrar(familia, "Arroz 5kg", new BigDecimal("27.50"));
        assertThat(naDespensa.getQtd()).isEqualTo(2);
        assertThat(naDespensa.getDataEntrada()).isEqualTo(LocalDate.now());
        verify(compras).delete(arroz);
    }

    @Test
    void comprarAlgoQueJaEstaNaDespensaSomaAQuantidade() {
        Compra cafe = compra(1L, "Café", 3);
        ItemDispensa jaTem = new ItemDispensa();
        jaTem.setNome("café");
        jaTem.setCategoria("Geral");
        jaTem.setQtd(1);
        when(compras.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(cafe));
        when(despensa.findByFamiliaIdAndNomeIgnoreCaseAndCategoria(FAMILIA, "Café", "Geral")).thenReturn(Optional.of(jaTem));

        compraService.comprarItem(FAMILIA, 1L, null);

        assertThat(jaTem.getQtd()).isEqualTo(4);
        verify(despensa, never()).save(any());
        verify(precos, never()).registrar(any(), any(), any()); // sem preço, nada no histórico
    }

    @Test
    void precoZeroOuNegativoNaoEntraNoHistorico() {
        when(compras.findByIdAndFamiliaId(1L, FAMILIA)).thenReturn(Optional.of(compra(1L, "Leite", 1)));
        compraService.comprarItem(FAMILIA, 1L, new BigDecimal("-3"));
        verify(precos, never()).registrar(any(), any(), any());
    }

    @Test
    void naoCompraNemExcluiItemDeOutraFamilia() {
        when(compras.findByIdAndFamiliaId(1L, OUTRA)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> compraService.comprarItem(OUTRA, 1L, BigDecimal.TEN)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> compraService.excluir(OUTRA, 1L)).isInstanceOf(ResourceNotFoundException.class);
        verify(compras, never()).delete(any());
    }

    @Test
    void itemSemQuantidadeEntraComUm() {
        Compra c = compraService.criar(FAMILIA, new CompraRequest("Pão", null, "Padaria", Frequencia.DIARIA));
        assertThat(c.getQtd()).isEqualTo(1);
    }

    @Test
    void adicionarNaDespensaUmItemQueJaExisteSomaAQuantidade() {
        ItemDispensa feijao = new ItemDispensa();
        feijao.setNome("Feijão");
        feijao.setCategoria("Geral");
        feijao.setQtd(2);
        when(despensa.findByFamiliaIdAndNomeIgnoreCaseAndCategoria(FAMILIA, "feijão", "Geral")).thenReturn(Optional.of(feijao));

        dispensaService.adicionar(FAMILIA, new ItemDispensaRequest("feijão", 3, "Geral"));

        assertThat(feijao.getQtd()).isEqualTo(5);
        verify(despensa, never()).save(any());
    }

    @Test
    void quantidadeNaDespensaNuncaFicaNegativa() {
        ItemDispensa ovos = new ItemDispensa();
        ovos.setQtd(2);
        when(despensa.findByIdAndFamiliaId(9L, FAMILIA)).thenReturn(Optional.of(ovos));

        dispensaService.atualizarQtd(FAMILIA, 9L, -50);

        assertThat(ovos.getQtd()).isZero();
    }

    @Test
    void acabouNaDespensaVoltaParaAListaSemDuplicar() {
        ItemDispensa cafe = new ItemDispensa();
        cafe.setNome("Café");
        cafe.setCategoria("Geral");
        when(despensa.findByIdAndFamiliaId(9L, FAMILIA)).thenReturn(Optional.of(cafe));
        Compra naLista = compra(1L, "CAFÉ", 1);
        when(compras.findAllByFamiliaId(FAMILIA)).thenReturn(List.of(naLista));

        dispensaService.moverParaCompras(FAMILIA, 9L);

        assertThat(naLista.getQtd()).isEqualTo(2); // já estava na lista: só aumenta
        verify(compras, never()).save(any());
    }

    private Compra compra(Long id, String nome, int qtd) {
        Compra c = new Compra();
        c.setId(id);
        c.setNome(nome);
        c.setQtd(qtd);
        c.setCategoria("Geral");
        c.setFrequencia(Frequencia.MENSAL);
        return c;
    }
}
