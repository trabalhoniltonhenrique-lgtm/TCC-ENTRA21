package com.casacapital.backend.assinatura;

import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssinaturaServiceTest {

    private static final long F = 1L;
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 25);

    private final List<Despesa> lista = new ArrayList<>();
    private final List<AssinaturaIgnorada> ignoradas = new ArrayList<>();
    private AssinaturaIgnoradaRepository ignoradasRepo;
    private AssinaturaService service;

    @BeforeEach
    void setUp() {
        DespesaRepository dr = mock(DespesaRepository.class);
        ignoradasRepo = mock(AssinaturaIgnoradaRepository.class);
        FamiliaRepository fr = mock(FamiliaRepository.class);
        when(dr.findAllByFamiliaIdOrderByDataDesc(F)).thenReturn(lista);
        when(ignoradasRepo.findAllByFamiliaId(F)).thenReturn(ignoradas);
        when(fr.getReferenceById(F)).thenReturn(new Familia());
        service = new AssinaturaService(dr, ignoradasRepo, fr);

        // Netflix todo mês, com aumento em outubro
        add("NETFLIX.COM", "Lazer", 39.90, "2026-07-10");
        add("NETFLIX.COM", "Lazer", 39.90, "2026-08-10");
        add("NETFLIX.COM", "Lazer", 39.90, "2026-09-10");
        add("NETFLIX.COM", "Lazer", 44.90, "2026-10-10");
        // Academia local (não é "conhecida"), uma vez por mês e mesmo valor
        add("ACADEMIA CORPO SAO", "Lazer", 99.00, "2026-08-05");
        add("ACADEMIA CORPO SAO", "Lazer", 99.00, "2026-09-05");
        add("ACADEMIA CORPO SAO", "Lazer", 99.00, "2026-10-05");
        // Padaria várias vezes por mês: não é assinatura
        for (String d : List.of("2026-09-02", "2026-09-09", "2026-09-20", "2026-10-03", "2026-10-11")) add("PADARIA TIMBO", "Alimentação", 12.50, d);
        // Uber uma vez por mês mas com valores muito diferentes: não é assinatura
        add("DL*UberRides", "Transporte", 8.00, "2026-09-12");
        add("DL*UberRides", "Transporte", 87.67, "2026-10-12");
        // Spotify apareceu uma vez só, há pouco tempo: provável e nova
        add("EBW*Spotify - NuPay", "Lazer", 23.90, "2026-10-16");
        // Parcela não é assinatura, mesmo se repetindo
        add("Geladeira (3/10)", "Moradia", 450.00, "2026-09-13");
        add("Geladeira (4/10)", "Moradia", 450.00, "2026-10-13");
        // Disney parou em julho: inativa (não entra no total)
        add("DISNEY PLUS", "Lazer", 33.90, "2026-05-08");
        add("DISNEY PLUS", "Lazer", 33.90, "2026-06-08");
        add("DISNEY PLUS", "Lazer", 33.90, "2026-07-08");
    }

    @Test
    void detectaAssinaturasEIgnoraGastosComuns() {
        AssinaturasResponse r = service.listar(F, HOJE);

        assertEquals(List.of("ACADEMIA CORPO", "NETFLIX COM", "EBW SPOTIFY", "DISNEY PLUS"),
                r.assinaturas().stream().map(AssinaturasResponse.Assinatura::chave).toList());

        AssinaturasResponse.Assinatura netflix = r.assinaturas().get(1);
        assertEquals(new BigDecimal("44.90"), netflix.valor());
        assertEquals(new BigDecimal("12.5"), netflix.aumentoPct()); // 39,90 -> 44,90 = +5,00 / 39,90
        assertEquals(4, netflix.meses());
        assertTrue(netflix.ativa());
        assertFalse(netflix.nova());

        AssinaturasResponse.Assinatura spotify = r.assinaturas().get(2);
        assertTrue(spotify.provavel());
        assertTrue(spotify.nova());
        assertNull(spotify.aumentoPct());

        AssinaturasResponse.Assinatura disney = r.assinaturas().get(3);
        assertFalse(disney.ativa());

        // só as ativas: 99,00 + 44,90 + 23,90
        assertEquals(new BigDecimal("167.80"), r.totalMensal());
        assertEquals(new BigDecimal("2013.60"), r.totalAnual());
    }

    @Test
    void estabelecimentoMarcadoComoNaoAssinaturaSome() {
        AssinaturaIgnorada academia = new AssinaturaIgnorada();
        academia.setChave("ACADEMIA CORPO");
        ignoradas.add(academia);

        AssinaturasResponse r = service.listar(F, HOJE);
        assertFalse(r.assinaturas().stream().anyMatch(a -> a.chave().equals("ACADEMIA CORPO")));
        assertEquals(new BigDecimal("68.80"), r.totalMensal());
    }

    @Test
    void ignorarNaoDuplica() {
        when(ignoradasRepo.findByFamiliaIdAndChave(F, "NETFLIX COM")).thenReturn(Optional.of(new AssinaturaIgnorada()));
        service.ignorar(F, "NETFLIX COM");
        verify(ignoradasRepo, never()).save(any());
    }

    private void add(String nome, String cat, double valor, String data) {
        Despesa d = new Despesa();
        d.setNome(nome);
        d.setCategoria(cat);
        d.setValor(BigDecimal.valueOf(valor).setScale(2));
        d.setData(LocalDate.parse(data));
        lista.add(d);
    }
}
