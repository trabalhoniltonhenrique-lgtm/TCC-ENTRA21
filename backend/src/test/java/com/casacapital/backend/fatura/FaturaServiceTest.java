package com.casacapital.backend.fatura;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.conta.Conta;
import com.casacapital.backend.conta.ContaRepository;
import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.fatura.LeitorFaturaIA.FaturaExtraida;
import com.casacapital.backend.fatura.LeitorFaturaIA.LancamentoExtraido;
import com.casacapital.backend.fatura.dto.FaturaAnaliseResponse;
import com.casacapital.backend.fatura.dto.FaturaImportRequest;
import com.casacapital.backend.fatura.dto.FaturaImportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final byte[] PDF_FALSO = pdfEmBranco();

    private ContaRepository contas;
    private DespesaRepository despesas;
    private FaturaExtraida lida;
    private FaturaService service;

    @BeforeEach
    void setUp() {
        contas = mock(ContaRepository.class);
        despesas = mock(DespesaRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(FAMILIA)).thenReturn(new Familia());
        when(contas.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contas.findAllByFamiliaIdAndParcelaNumAndParcelaTotal(eq(FAMILIA), anyInt(), anyInt())).thenReturn(List.of());
        when(despesas.findAllByFamiliaIdAndData(eq(FAMILIA), any())).thenReturn(List.of());

        LeitorFaturaIA leitor = new LeitorFaturaIA("chave-teste", "claude-opus-5-5") {
            @Override
            public FaturaExtraida ler(byte[] arquivo, TipoArquivo tipo) {
                return lida;
            }
        };
        service = new FaturaService(leitor, contas, despesas, familias);
    }

    private static LancamentoExtraido lanc(String data, String desc, double valor, int atual, int total, String cat, String tipo) {
        return new LancamentoExtraido(data, desc, valor, atual, total, cat, tipo);
    }

    @Test
    void classificaLancamentosEReconheceOQueJaExiste() {
        Conta parcelaPendente = conta(40L, "GELADEIRA NOVA (3/10)", "450.00", false);
        when(contas.findAllByFamiliaIdAndParcelaNumAndParcelaTotal(FAMILIA, 3, 10)).thenReturn(List.of(parcelaPendente));
        Despesa netflix = new Despesa();
        netflix.setId(7L);
        netflix.setNome("Netflix.com");
        netflix.setValor(new BigDecimal("55.90"));
        when(despesas.findAllByFamiliaIdAndData(FAMILIA, LocalDate.of(2026, 9, 10))).thenReturn(List.of(netflix));

        lida = new FaturaExtraida("Nubank", "2026-10-20", 1000, List.of(
                lanc("2026-09-14", "MERCADO EXTRA", 312.45, 0, 0, "Alimentação", "COMPRA"),
                lanc("2026-07-01", "GELADEIRA NOVA", 450.00, 3, 10, "Moradia", "COMPRA"),
                lanc("2026-09-18", "MAGAZINE LUIZA", 199.90, 1, 6, "Categoria inventada", "COMPRA"),
                lanc("2026-09-10", "NETFLIX.COM", 55.90, 0, 0, "Lazer", "COMPRA"),
                lanc("2026-09-20", "ESTORNO LOJA X", 30.00, 0, 0, "Outros", "CREDITO")));

        FaturaAnaliseResponse r = service.analisar(FAMILIA, PDF_FALSO, null);

        assertEquals(LocalDate.of(2026, 10, 20), r.vencimento());
        assertEquals(4, r.itens().size());
        assertEquals(1, r.creditos().size());
        assertEquals(FaturaService.NOVA_DESPESA, r.itens().get(0).acao());
        assertEquals(FaturaService.QUITAR_PARCELA, r.itens().get(1).acao());
        assertEquals(40L, r.itens().get(1).contaId());
        assertEquals(LocalDate.of(2026, 10, 20), r.itens().get(1).data()); // parcela > 1 entra no vencimento
        assertEquals(FaturaService.NOVA_PARCELADA, r.itens().get(2).acao());
        assertEquals("Outros", r.itens().get(2).categoria());
        assertEquals(LocalDate.of(2026, 9, 18), r.itens().get(2).data()); // 1ª parcela entra na data da compra
        assertEquals(FaturaService.JA_IMPORTADA, r.itens().get(3).acao());
    }

    @Test
    void importarCriaDespesasParcelasEQuitaParcelaExistente() {
        Conta pendente = conta(40L, "GELADEIRA NOVA (3/10)", "450.00", false);
        when(contas.findByIdAndFamiliaId(40L, FAMILIA)).thenReturn(Optional.of(pendente));

        LocalDate venc = LocalDate.of(2026, 10, 20);
        FaturaImportResponse r = service.importar(FAMILIA, new FaturaImportRequest(venc, List.of(
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

        ArgumentCaptor<Despesa> lancadas = ArgumentCaptor.forClass(Despesa.class);
        verify(despesas, atLeastOnce()).save(lancadas.capture());
        assertEquals(3, lancadas.getAllValues().size()); // à vista + parcela quitada + parcela atual da nova compra
        assertTrue(lancadas.getAllValues().stream().anyMatch(d -> d.getOrigemConta() == pendente && "Moradia".equals(d.getCategoria())));
    }

    @Test
    void pdfComSenhaPedeSenhaEAbreComASenhaCerta() throws Exception {
        byte[] protegido;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            StandardProtectionPolicy politica = new StandardProtectionPolicy("dono", "12345", new AccessPermission());
            politica.setEncryptionKeyLength(128);
            doc.protect(politica);
            doc.save(out);
            protegido = out.toByteArray();
        }
        lida = new FaturaExtraida("Itaú", "", 0, List.of());

        ApiException semSenha = assertThrows(ApiException.class, () -> service.analisar(FAMILIA, protegido, null));
        assertEquals("PDF_PROTEGIDO", semSenha.getCode());
        ApiException errada = assertThrows(ApiException.class, () -> service.analisar(FAMILIA, protegido, "000"));
        assertEquals("SENHA_INCORRETA", errada.getCode());
        assertNotNull(service.analisar(FAMILIA, protegido, "12345"));
    }

    @Test
    void recusaArquivoQueNaoEPdfNemImagem() {
        ApiException e = assertThrows(ApiException.class, () -> service.analisar(FAMILIA, "oi".getBytes(), null));
        assertEquals("ARQUIVO_INVALIDO", e.getCode());
    }

    private static byte[] pdfEmBranco() {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
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
