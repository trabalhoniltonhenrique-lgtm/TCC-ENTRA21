package com.casacapital.backend.investimento;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.investimento.dto.InvestimentoRequest;
import com.casacapital.backend.meta.Meta;
import com.casacapital.backend.meta.MetaRepository;
import com.casacapital.backend.meta.dto.MetaResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InvestimentoServiceTest {

    private InvestimentoRepository repo;
    private MetaRepository metas;
    private InvestimentoService service;
    private Meta reserva;

    @BeforeEach
    void setUp() {
        repo = mock(InvestimentoRepository.class);
        metas = mock(MetaRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(1L)).thenReturn(new Familia());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        reserva = new Meta();
        reserva.setId(7L);
        reserva.setNome("Reserva de emergência");
        reserva.setValorAlvo(new BigDecimal("10000.00"));
        when(metas.findByIdAndFamiliaId(7L, 1L)).thenReturn(Optional.of(reserva));
        service = new InvestimentoService(repo, familias, metas);
    }

    private static InvestimentoRequest req(String tipo, String atual, Long metaId) {
        return new InvestimentoRequest("CDB Banco X", tipo, "Banco X", new BigDecimal("5000.00"), new BigDecimal(atual),
                LocalDate.of(2026, 1, 10), LocalDate.of(2028, 1, 10), " ", metaId);
    }

    @Test
    void criaInvestimentoLigadoAMeta() {
        Investimento i = service.criar(1L, req("CDB", "5230.15", 7L));
        assertEquals(reserva, i.getMeta());
        assertEquals(LocalDate.now(), i.getAtualizadoEm());
        assertNull(i.getObs()); // campo em branco vira null
    }

    @Test
    void recusaTipoInvalido() {
        ApiException e = assertThrows(ApiException.class, () -> service.criar(1L, req("Bitcoin na gaveta", "1.00", null)));
        assertEquals("TIPO_INVALIDO", e.getCode());
    }

    @Test
    void progressoDaMetaSomaAportesEInvestimentosLigados() {
        Investimento cdb = service.criar(1L, req("CDB", "5230.15", 7L));
        Investimento tesouro = service.criar(1L, req("Tesouro Direto", "3000.00", 7L));
        Investimento solto = service.criar(1L, req("Ações", "999.99", null));
        when(repo.findAllByFamiliaId(1L)).thenReturn(List.of(cdb, tesouro, solto));

        Map<Long, BigDecimal> porMeta = service.totalPorMeta(1L);
        assertEquals(new BigDecimal("8230.15"), porMeta.get(7L));

        reserva.getAportes().add(new com.casacapital.backend.meta.MetaAporte(reserva, new BigDecimal("1000.00"), LocalDate.now()));
        MetaResponse r = MetaResponse.from(reserva, porMeta.get(7L));
        assertEquals(new BigDecimal("1000.00"), r.totalAportes());
        assertEquals(new BigDecimal("8230.15"), r.totalInvestimentos());
        assertEquals(new BigDecimal("9230.15"), r.totalPoupado());
        assertFalse(r.concluida());

        when(repo.findByIdAndFamiliaId(cdb.getId(), 1L)).thenReturn(Optional.of(cdb));
        service.atualizarValor(1L, cdb.getId(), new BigDecimal("6000.00"));
        assertTrue(MetaResponse.from(reserva, service.totalPorMeta(1L).get(7L)).concluida()); // 1000 + 6000 + 3000
    }
}
