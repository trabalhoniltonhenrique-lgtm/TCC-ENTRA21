package com.casacapital.backend.historicoprecos;

import com.casacapital.backend.compra.Compra;
import com.casacapital.backend.compra.CompraRepository;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.Produto;
import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse.TotalMercado;
import com.casacapital.backend.nfce.NfceServiceGtin;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ComparacaoMercadosServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 27);
    private final List<HistoricoPreco> hist = new ArrayList<>();
    private final ComparacaoMercadosService service =
            new ComparacaoMercadosService(mock(HistoricoPrecoRepository.class), mock(CompraRepository.class));
    private long seq = 1;

    private void preco(String nome, String mercado, String codigo, String valor, String data) {
        HistoricoPreco h = new HistoricoPreco(null, HistoricoPreco.chave(nome), LocalDate.parse(data), new BigDecimal(valor));
        h.setId(seq++);
        h.setMercado(mercado);
        h.setCodigoBarras(codigo);
        hist.add(h);
    }

    private static Compra item(String nome, int qtd) {
        Compra c = new Compra();
        c.setNome(nome);
        c.setQtd(qtd);
        return c;
    }

    private void cenario() {
        // mesmo arroz com nomes diferentes: reconhecido pelo código de barras
        preco("ARROZ TIO JOAO T1 5KG", "Bistek", "07893500020110", "24.90", "2026-10-05");
        preco("ARROZ T.JOAO TIPO1 5KG", "Giassi", "07893500020110", "22.50", "2026-10-12");
        // leite: no Bistek subiu de 4,59 para 5,29 (+15,3%)
        preco("LEITE UHT INTEGRAL ITALAC 1L", "Bistek", null, "4.59", "2026-09-20");
        preco("LEITE UHT INTEGRAL ITALAC 1L", "Bistek", null, "5.29", "2026-10-05");
        preco("LEITE UHT INTEGRAL ITALAC 1L", "Giassi", null, "4.99", "2026-10-12");
        preco("LEITE CONDENSADO MOCA 395G", "Giassi", null, "7.49", "2026-10-12");
        // banana só no Giassi; compra manual (sem mercado) não entra na comparação
        preco("BANANA PRATA KG", "Giassi", null, "6.99", "2026-10-12");
        preco("cafe", null, null, "18.00", "2026-10-01");
    }

    @Test
    void mesmoProdutoEmMercadosDiferentes() {
        cenario();
        ComparacaoMercadosResponse r = service.comparar(hist, List.of(), HOJE);

        assertEquals(List.of("Giassi", "Bistek"), r.mercados().stream().map(ComparacaoMercadosResponse.Mercado::nome).toList());
        assertEquals(2, r.produtos().size()); // arroz e leite integral estão nos dois
        Produto arroz = r.produtos().get(0);  // maior economia primeiro (R$ 2,40)
        assertEquals("Arroz T.joao Tipo1 5kg", arroz.nome());
        assertEquals("Giassi", arroz.maisBarato());
        assertEquals(new BigDecimal("2.40"), arroz.economia());
        assertEquals(9.6, arroz.economiaPct());
        Produto leite = r.produtos().get(1);
        assertEquals("Giassi", leite.maisBarato());
        assertEquals(new BigDecimal("5.29"), leite.precos().get(1).preco()); // último preço do Bistek
    }

    @Test
    void listaDeComprasEstimadaEmCadaMercado() {
        cenario();
        ComparacaoMercadosResponse r = service.comparar(hist,
                List.of(item("Arroz", 1), item("Leite integral", 6), item("Bananas", 2), item("Sabão em pó", 1)), HOJE);

        var itens = r.lista().itens();
        assertEquals("Arroz T.joao Tipo1 5kg", itens.get(0).produto());
        assertEquals("Leite Uht Integral Italac 1l", itens.get(1).produto()); // não o condensado
        assertEquals("Banana Prata Kg", itens.get(2).produto());               // plural
        assertNull(itens.get(3).produto());                                    // nunca comprado

        assertEquals(2, r.lista().itensCestaComum()); // arroz e leite têm preço nos dois
        TotalMercado giassi = r.lista().totais().get(0), bistek = r.lista().totais().get(1);
        assertEquals("Giassi", giassi.mercado());
        assertEquals(new BigDecimal("66.42"), giassi.total());          // 22,50 + 6×4,99 + 2×6,99
        assertEquals(new BigDecimal("52.44"), giassi.totalCestaComum()); // sem a banana
        assertEquals(1, giassi.itensSemPreco());
        assertEquals(new BigDecimal("56.64"), bistek.totalCestaComum()); // 24,90 + 6×5,29
        assertEquals(2, bistek.itensSemPreco());
    }

    @Test
    void precosQueSubiramNoMesmoMercado() {
        cenario();
        var altas = service.comparar(hist, List.of(), HOJE).altas();
        assertEquals(1, altas.size());
        assertEquals("Bistek", altas.get(0).mercado());
        assertEquals(15.3, altas.get(0).pct());
        // compra antiga (mais de 90 dias) não aparece
        assertTrue(service.comparar(hist, List.of(), HOJE.plusDays(200)).altas().isEmpty());
    }

    @Test
    void codigoDeBarrasValido() {
        assertEquals("07893500020110", NfceServiceGtin.gtin("7893500020110"));
        assertEquals("00000096385074", NfceServiceGtin.gtin("96385074"));   // GTIN-8
        assertNull(NfceServiceGtin.gtin("7893500020111"));                 // dígito verificador errado
        assertNull(NfceServiceGtin.gtin("2001"));                          // código interno do mercado
        assertNull(NfceServiceGtin.gtin("0000000000000"));
    }
}
