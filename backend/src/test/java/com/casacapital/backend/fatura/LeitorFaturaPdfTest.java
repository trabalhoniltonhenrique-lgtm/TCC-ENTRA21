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
    void leLayoutDoNubank() {
        // Resumo com "Próximas faturas" e as transações na mesma página; final do cartão antes da loja
        FaturaLida f = LeitorFaturaPdf.ler(List.of("""
                Data de vencimento: 14 OUT 2026
                Total a pagar R$ 249,48
                PRÓXIMAS FATURAS
                Fechamento da próxima fatura 07 NOV 2026
                Saldo em aberto da próxima fatura R$ 958,38
                TRANSAÇÕES DE 07 SET A 07 OUT
                07 SET •••• 4939 Amazon Retail Br Cpi - Parcela 12/12 R$ 89,40
                15 SET Raia Drogasil - NuPay R$ 25,48
                15 SET Estorno de Raia Drogasil - NuPay −R$ 25,48
                16 SET EBW*Spotify - NuPay R$ 23,90
                24 SET IOF de "Patreon* Membership" R$ 0,91
                01 OUT •••• 5321 Supermercado Schutze R$ 135,27
                """), HOJE);

        assertEquals(LocalDate.of(2026, 10, 14), f.vencimento());
        assertEquals(6, f.lancamentos().size()); // a seção de próximas faturas termina no bloco de transações

        Lancamento amazon = f.lancamentos().get(0);
        assertEquals("Amazon Retail Br Cpi", amazon.descricao()); // sem o "•••• 4939"
        assertEquals("Outros", amazon.categoria());
        assertEquals(12, amazon.parcelaAtual());

        assertTrue(f.lancamentos().get(2).credito()); // estorno com sinal de menos tipográfico
        assertEquals("Lazer", f.lancamentos().get(3).categoria());
        assertEquals("Contas & Serviços", f.lancamentos().get(4).categoria());
        assertEquals("Supermercado Schutze", f.lancamentos().get(5).descricao());
        assertEquals("Alimentação", f.lancamentos().get(5).categoria());
    }

    @Test
    void leLayoutDoInter() {
        // Datas "16 de jun. 2026", " - R$" como separador, "- + R$" no pagamento, rótulos com o valor na linha de baixo,
        // e o total a pagar menor que as compras porque parte foi antecipada
        FaturaLida f = LeitorFaturaPdf.ler(List.of("""
                Total da sua fatura
                R$ 63,40
                Data de Vencimento
                22/10/2026
                Valor total financiado R$ 50,00
                Faça o pagamento pela conta do Inter ou via Pix.
                SR. CAIXA FAVOR NÃO ACEITAR PAGAMENTO APÓS O VENCIMENTO
                BENEFICIÁRIO BANCO INTER S/A
                """, """
                Descritivo detalhado
                Despesas do mês R$ 163,40
                CARTÃO 2306****1111
                Data Movimentação Beneficiário Valor
                16 de jun. 2026 LOJAS BENOIT (Parcela 04 de 10) - R$ 100,00
                22 de set. 2026 PAGAMENTO ON LINE - + R$ 100,00
                21 de set. 2026 DL*UberRides - R$ 55,45
                27 de set. 2026 IFD*IFOOD CLUB - R$ 7,95
                Total CARTÃO 2306****1111 R$ 163,40
                Próxima fatura
                LOJAS BENOIT (Parcela 05 de 10) R$ 100,00
                """), HOJE);

        assertEquals("Inter", f.emissor()); // "SR. CAIXA" do boleto não conta
        assertEquals(LocalDate.of(2026, 10, 22), f.vencimento());
        assertEquals(new BigDecimal("63.40"), f.total()); // não o "valor total financiado"
        assertEquals(new BigDecimal("163.40"), f.totalLancamentos());
        assertEquals(3, f.lancamentos().size()); // o pagamento antecipado fica de fora

        Lancamento benoit = f.lancamentos().get(0);
        assertEquals(LocalDate.of(2026, 6, 16), benoit.data());
        assertEquals("LOJAS BENOIT", benoit.descricao());
        assertEquals(4, benoit.parcelaAtual());
        assertEquals(10, benoit.parcelaTotal());
        assertFalse(benoit.credito()); // " - R$" é separador, não sinal de menos

        assertEquals("Transporte", f.lancamentos().get(1).categoria());
        assertEquals("Alimentação", f.lancamentos().get(2).categoria());
    }

    @Test
    void leLayoutDaCaixa() {
        // Letra D/C colada no valor, parcela "04 DE 05" sem a palavra parcela, anuidade sem data, "CARTÕES CAIXA"
        FaturaLida f = LeitorFaturaPdf.ler(List.of("""
                VENCIMENTO
                23/09/2026
                VALOR TOTAL DESTA FATURA
                R$ 330,43
                CARTÕES CAIXA - 00.360.305/0001-04
                Demonstrativo
                13/08 TOTAL DA FATURA ANTERIOR 1.543,54D
                14/08 OBRIGADO PELO PAGAMENTO 1.543,54C
                11/09 CASHBACK ANUIDADE GASTOS TIT 8,62C
                Total 8,62 C
                ANUIDADE DIFERENCIADA TIT 05/   12 17,25D
                COMPRAS (Cartão 1116)
                28/08 DL*UberRides SAO PAULO 87,67D
                30/08 SUPERMERCADO SCHUTZE TIMBO 194,56D
                Total COMPRAS 282,23D
                COMPRAS PARCELADAS (Cartão 1116)
                14/05 AMAZON MARKETPLACE        04 DE 05 SAO PAULO 39,57D
                Valor total desta fatura R$ 330,43 D
                """), HOJE);

        assertEquals("Caixa", f.emissor());
        assertEquals(LocalDate.of(2026, 9, 23), f.vencimento());
        assertEquals(new BigDecimal("330.43"), f.total());
        assertEquals(5, f.lancamentos().size()); // fatura anterior, pagamento e totais ficam de fora

        Lancamento cashback = f.lancamentos().get(0);
        assertTrue(cashback.credito()); // "C" depois do valor

        Lancamento anuidade = f.lancamentos().get(1);
        assertEquals("ANUIDADE DIFERENCIADA TIT", anuidade.descricao());
        assertEquals(LocalDate.of(2026, 9, 23), anuidade.data()); // sem data: entra no vencimento
        assertEquals(5, anuidade.parcelaAtual());
        assertEquals(12, anuidade.parcelaTotal());
        assertFalse(anuidade.credito());

        assertFalse(f.lancamentos().get(2).credito()); // "D" depois do valor
        Lancamento amazon = f.lancamentos().get(4);
        assertEquals(4, amazon.parcelaAtual());
        assertEquals(5, amazon.parcelaTotal());

        BigDecimal soma = BigDecimal.ZERO;
        for (Lancamento l : f.lancamentos()) soma = l.credito() ? soma.subtract(l.valor()) : soma.add(l.valor());
        assertEquals(f.total(), soma);
    }

    @Test
    void semVencimentoUsaHojeComoReferencia() {
        FaturaLida f = LeitorFaturaPdf.ler(List.of("01/10 PADARIA PAO QUENTE 12,50"), HOJE);
        assertNull(f.vencimento());
        assertNull(f.emissor());
        assertEquals(LocalDate.of(2026, 10, 1), f.lancamentos().get(0).data());
    }
}
