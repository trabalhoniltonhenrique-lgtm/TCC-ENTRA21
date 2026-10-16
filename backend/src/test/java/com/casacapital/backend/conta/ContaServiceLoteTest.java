package com.casacapital.backend.conta;

import com.casacapital.backend.despesa.Despesa;
import com.casacapital.backend.despesa.DespesaRepository;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContaServiceLoteTest {

    @Test
    void pagaVariasContasDeUmaVezEIgnoraAsJaPagas() {
        ContaRepository contas = mock(ContaRepository.class);
        DespesaRepository despesas = mock(DespesaRepository.class);
        FamiliaRepository familias = mock(FamiliaRepository.class);
        when(familias.getReferenceById(1L)).thenReturn(new Familia());
        Conta luz = conta(1L, "Energia", Recorrencia.UNICA, false, null);
        Conta netflix = conta(2L, "Netflix", Recorrencia.MENSAL, false, "Nubank");
        Conta paga = conta(3L, "Aluguel", Recorrencia.UNICA, true, null);
        for (Conta c : List.of(luz, netflix, paga)) {
            when(contas.findByIdAndFamiliaId(c.getId(), 1L)).thenReturn(Optional.of(c));
        }
        ContaService service = new ContaService(contas, despesas, familias);

        List<Conta> pagas = service.marcarPagoEmLote(1L, List.of(1L, 2L, 3L, 2L));

        assertEquals(List.of(luz, netflix), pagas); // a já paga e o id repetido ficam de fora
        assertTrue(luz.isPago());
        assertTrue(netflix.isPago());
        verify(despesas, times(2)).save(any(Despesa.class));
        // a mensal gera a próxima ocorrência, levando o cartão junto
        verify(contas).save(org.mockito.ArgumentMatchers.argThat(c ->
                "Netflix".equals(c.getDescricao()) && "Nubank".equals(c.getCartao()) && !c.isPago()));
    }

    private static Conta conta(Long id, String desc, Recorrencia rec, boolean pago, String cartao) {
        Conta c = new Conta();
        c.setId(id);
        c.setFamilia(new Familia());
        c.setDescricao(desc);
        c.setCategoria("Outros");
        c.setValor(new BigDecimal("10.00"));
        c.setVencimento(LocalDate.of(2026, 10, 20));
        c.setRecorrencia(rec);
        c.setPago(pago);
        c.setCartao(cartao);
        return c;
    }
}
