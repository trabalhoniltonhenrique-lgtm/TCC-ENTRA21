package com.casacapital.backend.fatura;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.conta.Conta;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.fatura.dto.ContaConsumoImportRequest;
import com.casacapital.backend.fatura.dto.FaturaAnaliseResponse;
import com.casacapital.backend.fatura.dto.FaturaImportRequest;
import com.casacapital.backend.fatura.dto.FaturaImportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaturaServiceTest {

    private static final long FAMILIA = 1L;

    private ContaRepository contas;
    private DespesaRepository despesas;
    private FaturaService service;
    private CategoriaAprendidaRepository aprendidas;

    @BeforeEach
    void setUp() {
        contas = mock(ContaRepository.class);
        despesas = mock(DespesaRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(FAMILIA)).thenReturn(new Familia());
        when(contas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contas.findAllByFamiliaIdAndParcelaNumAndParcelaTotal(eq(FAMILIA), anyInt(), anyInt())).thenReturn(List.of());
        when(despesas.findAllByFamiliaIdAndData(eq(FAMILIA), any())).thenReturn(List.of());
        aprendidas = mock(CategoriaAprendidaRepository.class);
        when(aprendidas.findAllByFamiliaId(FAMILIA)).thenReturn(List.of());
        service = new FaturaService(contas, despesas, familias, aprendidas);
    }

    @Test
    void leOPdfEReconheceOQueJaExiste() throws Exception {
        Conta parcelaPendente = conta(40L, "GELADEIRA NOVA (3/10)", "450.00", false);
        when(contas.findAllByFamiliaIdAndParcelaNumAndParcelaTotal(FAMILIA, 3, 10)).thenReturn(List.of(parcelaPendente));
        Despesa netflix = new Despesa();
        netflix.setId(7L);
        netflix.setNome("Netflix.com");
        netflix.setValor(new BigDecimal("55.90"));
        when(despesas.findAllByFamiliaIdAndData(FAMILIA, LocalDate.of(2026, 9, 10))).thenReturn(List.of(netflix));

        byte[] pdf = pdf(null,
                "Nubank - Vencimento 20/10/2026",
                "14/09 MERCADO EXTRA 312,45",
                "01/07 GELADEIRA NOVA 03/10 450,00",
                "18/09 MAGAZINE LUIZA 01/06 199,90",
                "10/09 NETFLIX.COM 55,90",
                "20/09 ESTORNO LOJA X -30,00");

        FaturaAnaliseResponse r = service.analisar(FAMILIA, pdf, null);

        assertEquals("Nubank", r.emissor());
        assertEquals(LocalDate.of(2026, 10, 20), r.vencimento());
        assertEquals(4, r.itens().size());
        assertEquals(1, r.creditos().size());
        assertEquals(FaturaService.NOVA_DESPESA, r.itens().get(0).acao());
        assertEquals("Alimentação", r.itens().get(0).categoria());
        assertEquals(FaturaService.QUITAR_PARCELA, r.itens().get(1).acao());
        assertEquals(40L, r.itens().get(1).contaId());
        assertEquals(LocalDate.of(2026, 10, 20), r.itens().get(1).data()); // parcela > 1 entra no vencimento
        assertEquals(FaturaService.NOVA_PARCELADA, r.itens().get(2).acao());
        assertEquals(LocalDate.of(2026, 9, 18), r.itens().get(2).data()); // 1ª parcela entra na data da compra
        assertEquals(FaturaService.JA_IMPORTADA, r.itens().get(3).acao());
    }

    @Test
    void lembraACategoriaEscolhidaParaOEstabelecimento() throws Exception {
        CategoriaAprendida cooper = new CategoriaAprendida();
        cooper.setChave("COOPER FILIAL");
        cooper.setCategoria("Moradia");
        when(aprendidas.findAllByFamiliaId(FAMILIA)).thenReturn(List.of(cooper));

        FaturaAnaliseResponse r = service.analisar(FAMILIA, pdf(null,
                "Vencimento 20/10/2026", "13/09 COOPER FILIAL TIMBO 58,84", "14/09 PADARIA CENTRO 12,00"), null);

        assertEquals("Moradia", r.itens().get(0).categoria()); // a escolha da família vence a palavra-chave
        assertTrue(r.itens().get(0).categoriaLembrada());
        assertEquals("Alimentação", r.itens().get(1).categoria());
        assertFalse(r.itens().get(1).categoriaLembrada());

        service.importar(FAMILIA, new FaturaImportRequest(LocalDate.of(2026, 10, 20), null, List.of(
                new FaturaImportRequest.Item(LocalDate.of(2026, 9, 13), "COOPER FILIAL TIMBO", new BigDecimal("58.84"), null, null, "Alimentação", null),
                new FaturaImportRequest.Item(LocalDate.of(2026, 9, 14), "DL *UberRides Sao Paulo", new BigDecimal("12.00"), null, null, "Transporte", null))));
        assertEquals("Alimentação", cooper.getCategoria()); // a nova escolha substitui a antiga
        ArgumentCaptor<CategoriaAprendida> salvas = ArgumentCaptor.forClass(CategoriaAprendida.class);
        verify(aprendidas, org.mockito.Mockito.times(2)).save(salvas.capture());
        assertEquals("DL UBERRIDES", salvas.getAllValues().get(1).getChave());
    }

    @Test
    void chaveDoEstabelecimentoIgnoraNumerosEPontuacao() {
        assertEquals("DL UBERRIDES", FaturaService.chaveLoja("DL*UberRides SAO PAULO"));
        assertEquals("DL UBERRIDES", FaturaService.chaveLoja("DL          *UberRides Sao Paulo"));
        assertEquals("SUPERMERCADO SCHUTZE", FaturaService.chaveLoja("SUPERMERCADO SCHUTZE TIMBO"));
        assertEquals("RAIA DROGASIL", FaturaService.chaveLoja("5321 Raia Drogasil - NuPay"));
    }

    @Test
    void importarCriaDespesasParcelasEQuitaParcelaExistente() {
        Conta pendente = conta(40L, "GELADEIRA NOVA (3/10)", "450.00", false);
        when(contas.findByIdAndFamiliaId(40L, FAMILIA)).thenReturn(Optional.of(pendente));

        LocalDate venc = LocalDate.of(2026, 10, 20);
        FaturaImportResponse r = service.importar(FAMILIA, new FaturaImportRequest(venc, "Itaú", List.of(
                new FaturaImportRequest.Item(LocalDate.of(2026, 9, 14), "MERCADO EXTRA", new BigDecimal("312.45"), null, null, "Alimentação", null),
                new FaturaImportRequest.Item(venc, "GELADEIRA NOVA", new BigDecimal("450.00"), 3, 10, "Moradia", 40L),
                new FaturaImportRequest.Item(LocalDate.of(2026, 9, 18), "MAGAZINE LUIZA", new BigDecimal("199.90"), 4, 6, "Vestuário", null))));

        assertEquals(1, r.despesasCriadas());
        assertEquals(1, r.parcelamentosCriados());
        assertEquals(1, r.parcelasQuitadas());
        assertEquals(new BigDecimal("962.35"), r.totalImportado());

        assertTrue(pendente.isPago());
        assertEquals("Moradia", pendente.getCategoriaDespesa());

        ArgumentCaptor<Conta> novas = ArgumentCaptor.forClass(Conta.class);
        verify(contas, atLeastOnce()).save(novas.capture());
        List<Conta> parcelas = novas.getAllValues();
        assertEquals(3, parcelas.size()); // parcelas 4, 5 e 6
        assertEquals("MAGAZINE LUIZA (4/6)", parcelas.get(0).getDescricao());
        assertTrue(parcelas.get(0).isPago());
        assertEquals(venc.plusMonths(2), parcelas.get(2).getVencimento());
        assertEquals("Vestuário", parcelas.get(2).getCategoriaDespesa());
        assertEquals("Itaú", parcelas.get(2).getCartao());
        assertEquals("Itaú", pendente.getCartao()); // parcela quitada sem cartão recebe o da fatura

        ArgumentCaptor<Despesa> lancadas = ArgumentCaptor.forClass(Despesa.class);
        verify(despesas, atLeastOnce()).save(lancadas.capture());
        assertEquals(3, lancadas.getAllValues().size()); // à vista + parcela quitada + parcela atual da nova compra
        assertTrue(lancadas.getAllValues().stream().anyMatch(d -> d.getOrigemConta() == pendente && "Moradia".equals(d.getCategoria())));
    }

    @Test
    void pdfComSenhaPedeSenhaEAbreComASenhaCerta() throws Exception {
        byte[] protegido = pdf("12345", "10/09 PADARIA 12,50");

        ApiException semSenha = assertThrows(ApiException.class, () -> service.analisar(FAMILIA, protegido, null));
        assertEquals("PDF_PROTEGIDO", semSenha.getCode());
        ApiException errada = assertThrows(ApiException.class, () -> service.analisar(FAMILIA, protegido, "000"));
        assertEquals("SENHA_INCORRETA", errada.getCode());
        assertNotNull(service.analisar(FAMILIA, protegido, "12345"));
    }

    @Test
    void recusaArquivoQueNaoEPdf() {
        ApiException e = assertThrows(ApiException.class, () -> service.analisar(FAMILIA, "oi".getBytes(), null));
        assertEquals("ARQUIVO_INVALIDO", e.getCode());
    }

    @Test
    void avisaQuandoOPdfNaoTemLancamentos() throws Exception {
        byte[] semTexto = pdf(null, "Fatura escaneada sem lancamentos legiveis");
        ApiException e = assertThrows(ApiException.class, () -> service.analisar(FAMILIA, semTexto, null));
        assertEquals("SEM_LANCAMENTOS", e.getCode());
    }

    @Test
    void contaDeLuzPropoeAtualizarAContaEmAbertoDoMes() throws Exception {
        Conta mensal = conta(50L, "Energia elétrica", "120.00", false);
        mensal.setCategoria("Energia");
        mensal.setVencimento(LocalDate.of(2026, 10, 15));
        mensal.setRecorrencia(com.casacapital.backend.conta.Recorrencia.MENSAL);
        when(contas.findAllByFamiliaIdOrderByVencimentoAsc(FAMILIA)).thenReturn(List.of(mensal));
        when(contas.findByIdAndFamiliaId(50L, FAMILIA)).thenReturn(Optional.of(mensal));

        byte[] pdf = pdf(null, "Consumo TE KWH 100,000 39,89", "Celesc Distribuicao SA", "Referencia 10/2026",
                "Vencimento 20/10/2026", "Total a Pagar (R$) 98,70");
        FaturaAnaliseResponse r = service.analisar(FAMILIA, pdf, null);

        assertEquals("CONTA_CONSUMO", r.tipo());
        assertEquals("Energia", r.conta().categoria());
        assertEquals(new BigDecimal("98.70"), r.conta().valor());
        assertEquals(50L, r.conta().contaId());

        Conta salva = service.importarContaConsumo(FAMILIA, new ContaConsumoImportRequest(
                r.conta().descricao(), "Energia", r.conta().valor(), r.conta().vencimento(), 50L));
        assertEquals(mensal, salva);
        assertEquals(new BigDecimal("98.70"), mensal.getValor());
        assertEquals(LocalDate.of(2026, 10, 20), mensal.getVencimento());
        assertEquals(com.casacapital.backend.conta.Recorrencia.MENSAL, mensal.getRecorrencia()); // continua mensal
    }

    @Test
    void contaDeLuzSemContaNoMesCriaUmaNova() throws Exception {
        byte[] pdf = pdf(null, "Consumo TE KWH 100,000 39,89", "Celesc Distribuicao SA",
                "Vencimento 20/10/2026", "Total a Pagar (R$) 98,70");
        FaturaAnaliseResponse r = service.analisar(FAMILIA, pdf, null);
        assertEquals(null, r.conta().contaId());

        Conta nova = service.importarContaConsumo(FAMILIA, new ContaConsumoImportRequest(
                "Energia elétrica - Celesc", "Energia", new BigDecimal("98.70"), LocalDate.of(2026, 10, 20), null));
        assertEquals("Energia", nova.getCategoria());
        assertEquals(LocalDate.of(2026, 10, 20), nova.getVencimento());
        assertEquals(com.casacapital.backend.conta.Recorrencia.UNICA, nova.getRecorrencia());
        verify(contas).save(nova);
    }

    /** Gera um PDF com uma linha de texto por item (senha opcional). */
    private static byte[] pdf(String senha, String... linhas) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                float y = 750;
                for (String linha : linhas) {
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(linha);
                    cs.endText();
                    y -= 14;
                }
            }
            if (senha != null) {
                StandardProtectionPolicy politica = new StandardProtectionPolicy("dono", senha, new AccessPermission());
                politica.setEncryptionKeyLength(128);
                doc.protect(politica);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static Conta conta(Long id, String descricao, String valor, boolean pago) {
        Conta c = new Conta();
        c.setId(id);
        c.setFamilia(new Familia());
        c.setDescricao(descricao);
        c.setCategoria("Cartão de Crédito");
        c.setValor(new BigDecimal(valor));
        c.setPago(pago);
        return c;
    }
}
