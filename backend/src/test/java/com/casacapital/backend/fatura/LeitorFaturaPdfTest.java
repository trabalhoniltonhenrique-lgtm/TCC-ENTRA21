package com.casacapital.backend.fatura;

import com.casacapital.backend.fatura.LeitorFaturaPdf.FaturaLida;
import com.casacapital.backend.fatura.LeitorFaturaPdf.Lancamento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeitorFaturaPdfTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 12);

    @Test
    void leFaturaComMesPorExtenso() {
        FaturaLida f = LeitorFaturaPdf.ler(List.of("""
                Nu Pagamentos S.A.
                Data do vencimento: 20/10/2026
                Total a pagar R$ 1.406,29
                TRANSAÇÕES DE 14 SET A 13 OUT
                14 SET Mercado Extra 123 312,45
                16 SET Posto Shell R$ 220,00
                18 SET Magazine Luiza - Parcela 1/6 199,90
                20 SET Estorno Loja X -30,00
                22 SET Pagamento recebido -1.200,00
                """), HOJE);

        assertEquals("Nubank", f.emissor());
        assertEquals(LocalDate.of(2026, 10, 20), f.vencimento());
        assertEquals(new BigDecimal("1406.29"), f.total());
        assertEquals(4, f.lancamentos().size()); // o pagamento da fatura anterior fica de fora

        Lancamento mercado = f.lancamentos().get(0);
        assertEquals(LocalDate.of(2026, 9, 14), mercado.data());
        assertEquals("Mercado Extra 123", mercado.descricao());
        assertEquals(new BigDecimal("312.45"), mercado.valor());
        assertEquals("Alimentação", mercado.categoria());
        assertEquals(0, mercado.parcelaTotal());
        assertFalse(mercado.credito());

        assertEquals("Transporte", f.lancamentos().get(1).categoria());

        Lancamento magalu = f.lancamentos().get(2);
        assertEquals("Magazine Luiza", magalu.descricao());
        assertEquals(1, magalu.parcelaAtual());
        assertEquals(6, magalu.parcelaTotal());

        assertTrue(f.lancamentos().get(3).credito());
    }

    @Test
    void leFaturaComDataNumericaEParcelaNoFimDaDescricao() {
        FaturaLida f = LeitorFaturaPdf.ler(List.of("""
                ITAÚ UNIBANCO - Cartão Visa
                Vencimento 10/01/2027   Total desta fatura 2.050,10
                Lançamentos: compras e saques
                15/12 DROGARIA SAO PAULO       45,90
                20/12 GELADEIRA BRASTEMP 03/10    450,00
                02/01 NETFLIX.COM               55,90
                05/01 AMAZON US$ 10,00          52,30
                """, """
                Compras parceladas - próximas faturas
                20/12 GELADEIRA BRASTEMP 04/10    450,00
                """), HOJE);

        assertEquals("Itaú", f.emissor());
        assertEquals(LocalDate.of(2027, 1, 10), f.vencimento());
        assertEquals(new BigDecimal("2050.10"), f.total());
        assertEquals(4, f.lancamentos().size()); // a parcela 04/10 da seção de próximas faturas não entra

        assertEquals(LocalDate.of(2026, 12, 15), f.lancamentos().get(0).data()); // dezembro do ano anterior
        assertEquals("Saúde", f.lancamentos().get(0).categoria());

        Lancamento geladeira = f.lancamentos().get(1);
        assertEquals("GELADEIRA BRASTEMP", geladeira.descricao());
        assertEquals(3, geladeira.parcelaAtual());
        assertEquals(10, geladeira.parcelaTotal());

        assertEquals("Lazer", f.lancamentos().get(2).categoria());
        Lancamento amazon = f.lancamentos().get(3);
        assertEquals("AMAZON", amazon.descricao());
        assertEquals(new BigDecimal("52.30"), amazon.valor()); // valor em reais, não em dólar
    }

    @Test
    void escolheAOrdemDeLeituraQueBateComOTotal() {
        // Layout do Itaú em colunas: na ordem do arquivo sai limpo; ordenado pela posição, as colunas se misturam
        // e o texto explicativo menciona "próxima fatura" no meio da página.
        List<String> ordemDoArquivo = List.of("""
                Vencimento: 13/10/2026 = Total desta fatura 151,81
                Total da fatura anterior 912,34
                """, """
                Pagamentos efetuados
                07/09 Pagamento via conta -912,34
                Lançamentos: compras e saques
                08/01 MERCADOLIVRE*M 09/10 72,99
                RETAIL  BENTO GONCAL
                13/09 COOPER FILIAL CIDADE 58,84
                supermercado  CIDADE
                22/09 PADARIA CENTRO 20,00
                supermercado  CIDADE
                Total dos lançamentos atuais 151,83
                Compras parceladas - próximas faturas
                08/01 MERCADOLIVRE*M 10/10 72,99
                """);
        List<String> ordemDaPagina = List.of(ordemDoArquivo.get(0), """
                Caso você pague um valor menor, o saldo será cobrado na próxima fatura com juros.
                07/09 Pagamento via conta -912,34 22/09 PADARIA CENTRO 20,00
                08/01 MERCADOLIVRE*M 09/10 72,99
                """);

        FaturaLida f = LeitorFaturaPdf.lerMelhor(ordemDoArquivo, ordemDaPagina, HOJE);

        assertEquals(new BigDecimal("151.81"), f.total()); // "desta fatura", não a anterior
        assertEquals(3, f.lancamentos().size());
        assertEquals("Outros", f.lancamentos().get(0).categoria()); // MERCADOLIVRE não é supermercado
        assertEquals(9, f.lancamentos().get(0).parcelaAtual());
        assertEquals(LocalDate.of(2026, 1, 8), f.lancamentos().get(0).data());
        assertEquals("Alimentação", f.lancamentos().get(1).categoria()); // pela categoria impressa pelo banco
        assertFalse(f.lancamentos().stream().anyMatch(l -> l.descricao().startsWith("Pagamento")));
    }

    @Test
    void semVencimentoUsaHojeComoReferencia() {
        FaturaLida f = LeitorFaturaPdf.ler(List.of("01/10 PADARIA PAO QUENTE 12,50"), HOJE);
        assertNull(f.vencimento());
        assertNull(f.emissor());
        assertEquals(LocalDate.of(2026, 10, 1), f.lancamentos().get(0).data());
    }
}
