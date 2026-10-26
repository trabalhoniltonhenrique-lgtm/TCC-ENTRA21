package com.casacapital.backend.nfce;

import com.casacapital.backend.common.ApiException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeitorNfceTest {

    private static final String CHAVE = "42261012345678000190650010000123451000123456";
    private static final String LINK = "https://sat.sef.sc.gov.br/nfce/consulta?p=" + CHAVE + "|2|1|1|ABCDEF0123456789";

    // Trecho no layout padrão da consulta pública da NFC-e (tabela tabResult)
    private static final String HTML = """
            <html><head><meta charset="utf-8"></head><body>
            <div id="conteudo">
              <div class="txtCenter">
                <div id="u20" class="txtTopo">SUPERMERCADO BOM PRECO LTDA</div>
                <div class="text">CNPJ: 12.345.678/0001-90</div>
                <div class="text">RUA DAS FLORES, 100, CENTRO, FLORIANOPOLIS, SC</div>
              </div>
              <table id="tabResult">
                <tr id="Item + 1">
                  <td valign="top"><span class="txtTit">ARROZ TIO JOAO T1 5KG</span><span class="RCod">(Código: 7893500020110 )</span><br>
                    <span class="Rqtd"><strong>Qtde.:</strong>2</span><span class="RUN"><strong>UN: </strong>UN</span>
                    <span class="RvlUnit"><strong>Vl. Unit.:</strong>&nbsp;24,9</span></td>
                  <td align="right" valign="top" class="txtTit noWrap">Vl. Total<br><span class="valor">49,80</span></td>
                </tr>
                <tr id="Item + 2">
                  <td valign="top"><span class="txtTit">BANANA PRATA KG</span><span class="RCod">(Código: 2001 )</span><br>
                    <span class="Rqtd"><strong>Qtde.:</strong>1,235</span><span class="RUN"><strong>UN: </strong>KG</span>
                    <span class="RvlUnit"><strong>Vl. Unit.:</strong>&nbsp;6,99</span></td>
                  <td align="right" valign="top" class="txtTit noWrap">Vl. Total<br><span class="valor">8,63</span></td>
                </tr>
                <tr id="Item + 3">
                  <td valign="top"><span class="txtTit">DETERGENTE YPE 500ML</span><span class="RCod">(Código: 7896098900208 )</span><br>
                    <span class="Rqtd"><strong>Qtde.:</strong>3</span><span class="RUN"><strong>UN: </strong>UN</span>
                    <span class="RvlUnit"><strong>Vl. Unit.:</strong>&nbsp;2,79</span></td>
                  <td align="right" valign="top" class="txtTit noWrap">Vl. Total<br><span class="valor">8,37</span></td>
                </tr>
              </table>
              <div id="totalNota" class="txtRight">
                <div id="linhaTotal"><label>Qtd. total de itens:</label><span class="totalNumb">3</span></div>
                <div id="linhaTotal"><label>Valor total R$:</label><span class="totalNumb">66,80</span></div>
                <div id="linhaTotal"><label>Descontos R$:</label><span class="totalNumb">0,80</span></div>
                <div id="linhaTotal" class="linhaShade"><label>Valor a pagar R$:</label><span class="totalNumb txtMax">66,00</span></div>
              </div>
              <div id="infos"><ul><li><strong>Número: </strong>12345 <strong>Série: </strong>1 <strong>Emissão: </strong>25/10/2026 18:42:10 - Via Consumidor</li></ul>
                <span class="chave">4226 1012 3456 7800 0190 6500 1000 0123 4510 0012 3456</span></div>
            </div></body></html>""";

    @Test
    void leItensEmitenteDataETotalDaPaginaPadrao() throws Exception {
        NotaFiscalLida n = LeitorPaginaNfce.ler(LeitorPaginaNfce.abrir(HTML.getBytes(StandardCharsets.UTF_8), LINK), LINK);

        assertEquals(CHAVE, n.chave());
        assertEquals("SUPERMERCADO BOM PRECO LTDA", n.emitente());
        assertEquals("12.345.678/0001-90", n.cnpj());
        assertEquals(LocalDate.of(2026, 10, 25), n.dataEmissao());
        assertEquals(new BigDecimal("66.00"), n.total()); // valor a pagar, já com desconto
        assertEquals(3, n.itens().size());

        NotaFiscalLida.Item arroz = n.itens().get(0);
        assertEquals("ARROZ TIO JOAO T1 5KG", arroz.nome());
        assertEquals("7893500020110", arroz.codigo());
        assertEquals(new BigDecimal("2"), arroz.quantidade());
        assertEquals("UN", arroz.unidade());
        assertEquals(new BigDecimal("24.9"), arroz.valorUnitario());
        assertEquals(new BigDecimal("49.80"), arroz.valorTotal());

        NotaFiscalLida.Item banana = n.itens().get(1);
        assertEquals(new BigDecimal("1.235"), banana.quantidade());
        assertEquals("KG", banana.unidade());
        assertEquals(1, NfceService.qtdDespensa(banana)); // a granel conta 1
        assertEquals(2, NfceService.qtdDespensa(arroz));
    }

    @Test
    void semAsClassesLePeloTextoDaLinha() throws Exception {
        String html = """
                <table><tr><td>LEITE INTEGRAL ITALAC 1L (Código: 789) Qtde.:12 UN: UN Vl. Unit.: 4,59</td>
                <td>Vl. Total 55,08</td></tr></table><p>Valor a pagar R$: 55,08</p>""";
        NotaFiscalLida n = LeitorPaginaNfce.ler(LeitorPaginaNfce.abrir(html.getBytes(StandardCharsets.UTF_8), LINK), LINK);
        assertEquals(1, n.itens().size());
        assertEquals("LEITE INTEGRAL ITALAC 1L", n.itens().get(0).nome());
        assertEquals(new BigDecimal("12"), n.itens().get(0).quantidade());
        assertEquals(new BigDecimal("55.08"), n.total());
    }

    @Test
    void leOTextoCopiadoDaPaginaNoNavegador() throws Exception {
        // como fica ao "Selecionar tudo → Copiar" a página da nota
        String colado = """
                SUPERMERCADO BOM PRECO LTDA
                CNPJ: 12.345.678/0001-90
                RUA DAS FLORES, 100, CENTRO, FLORIANOPOLIS, SC
                ARROZ TIO JOAO T1 5KG
                (Código: 7893500020110 )
                Qtde.:2 UN: UN Vl. Unit.: 24,9
                Vl. Total
                49,80
                BANANA PRATA KG (Código: 2001 )
                Qtde.:1,235 UN: KG Vl. Unit.: 6,99
                Vl. Total
                8,63
                Qtd. total de itens: 2
                Valor total R$: 58,43
                Valor a pagar R$: 58,43
                Número: 12345 Série: 1 Emissão: 25/10/2026 18:42:10 - Via Consumidor
                Chave de acesso:
                4226 1012 3456 7800 0190 6500 1000 0123 4510 0012 3456
                """;
        NotaFiscalLida n = LeitorPaginaNfce.lerTexto(colado, null);
        assertEquals(2, n.itens().size());
        assertEquals("ARROZ TIO JOAO T1 5KG", n.itens().get(0).nome());
        assertEquals(new BigDecimal("49.80"), n.itens().get(0).valorTotal());
        assertEquals("BANANA PRATA KG", n.itens().get(1).nome());
        assertEquals(new BigDecimal("1.235"), n.itens().get(1).quantidade());
        assertEquals("KG", n.itens().get(1).unidade());
        assertEquals(new BigDecimal("58.43"), n.total());
        assertEquals(LocalDate.of(2026, 10, 25), n.dataEmissao());
        assertEquals(CHAVE, n.chave()); // sem link, pela chave escrita na página
    }

    @Test
    void reconheceAVerificacaoNaoSouUmRobo() {
        assertTrue(ConsultaSefaz.pedeVerificacaoHumana("https://sat.sef.sc.gov.br/tax.NET/SecurityVerify.aspx?rq=x", new byte[0]));
        assertTrue(ConsultaSefaz.pedeVerificacaoHumana("https://x.gov.br/n",
                "<div class=\"cf-turnstile\" data-sitekey=\"1\"></div>".getBytes(StandardCharsets.UTF_8)));
        assertFalse(ConsultaSefaz.pedeVerificacaoHumana(LINK, HTML.getBytes(StandardCharsets.UTF_8)));
        assertEquals("https://sat.sef.sc.gov.br/nfce/consulta?p=" + CHAVE + "%7C2%7C1%7C1%7CABCDEF0123456789",
                ConsultaSefaz.normalizar(LINK));
    }

    @Test
    void chaveDeAcessoPeloLinkDoQr() {
        assertEquals(CHAVE, LeitorPaginaNfce.chaveDoEndereco(LINK));
        assertEquals(CHAVE, LeitorPaginaNfce.chaveDoEndereco("http://www.nfce.fazenda.sp.gov.br/qrcode?chNFe=" + CHAVE + "&nVersao=100"));
        assertEquals(CHAVE, LeitorPaginaNfce.chaveDoEndereco("https://x.gov.br/c?p=" + CHAVE + "%7C2%7C1"));
        assertNull(LeitorPaginaNfce.chaveDoEndereco("https://sat.sef.sc.gov.br/nfce/consulta"));
    }

    @Test
    void numerosNoFormatoBrasileiro() {
        assertEquals(new BigDecimal("1234.56"), LeitorPaginaNfce.numero("R$ 1.234,56"));
        assertEquals(new BigDecimal("0.535"), LeitorPaginaNfce.numero("Qtde.: 0,535"));
        assertEquals(new BigDecimal("24.9"), LeitorPaginaNfce.numero("24.9"));
        assertNull(LeitorPaginaNfce.numero("—"));
    }

    @Test
    void soAbreSiteDeGovernoNaInternet() {
        // QR code de papel qualquer não pode fazer o servidor acessar outra coisa
        for (String ruim : new String[]{"https://exemplo.com/nfce?p=1", "http://localhost:8080/api/backup",
                "http://192.168.1.1/admin", "file:///C:/Windows/win.ini", "https://sat.sef.sc.gov.br.evil.com/x",
                "https://usuario@sat.sef.sc.gov.br/x", "https://sat.sef.sc.gov.br:8443/x", "texto qualquer"}) {
            ApiException e = assertThrows(ApiException.class, () -> ConsultaSefaz.validar(ruim), ruim);
            assertEquals("NFCE_LINK_INVALIDO", e.getCode(), ruim);
        }
    }

    @Test
    void sugereCategoriaDaDespensaPeloNome() {
        assertEquals("Hortifruti", NfceService.categoria("BANANA PRATA KG"));
        assertEquals("Limpeza", NfceService.categoria("DETERGENTE YPE 500ML"));
        assertEquals("Laticínios", NfceService.categoria("LEITE UHT INTEGRAL"));
        assertEquals("Carnes", NfceService.categoria("CARNE BOVINA MOIDA"));
        assertEquals("Higiene", NfceService.categoria("PAPEL HIG NEVE 12UN"));
        assertEquals("Padaria", NfceService.categoria("PÃO FRANCÊS KG"));
        assertEquals("Geral", NfceService.categoria("ARROZ TIO JOAO T1 5KG"));
    }

    @Test
    void leOQrCodeDeUmaFoto() throws Exception {
        // QR pequeno no meio de uma "foto" grande, como o da nota no papel
        BitMatrix m = new QRCodeWriter().encode(LINK, BarcodeFormat.QR_CODE, 300, 300);
        BufferedImage foto = new BufferedImage(3000, 4000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = foto.createGraphics();
        g.setColor(new Color(235, 232, 225));
        g.fillRect(0, 0, 3000, 4000);
        g.setColor(Color.BLACK);
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (m.get(x, y)) g.fillRect(1200 + x * 2, 1800 + y * 2, 2, 2);
            }
        }
        g.dispose();
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(foto, "jpg", jpg);

        assertEquals(LINK, LeitorQrCode.ler(jpg.toByteArray()).orElseThrow());
    }
}
