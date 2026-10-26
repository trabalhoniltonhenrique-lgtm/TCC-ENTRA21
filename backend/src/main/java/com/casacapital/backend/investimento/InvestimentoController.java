package com.casacapital.backend.investimento;

import com.casacapital.backend.investimento.dto.InvestimentoRequest;
import com.casacapital.backend.investimento.dto.InvestimentoResponse;
import com.casacapital.backend.investimento.dto.ValorAtualRequest;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/investimentos")
public class InvestimentoController {

    private final InvestimentoService service;

    public InvestimentoController(InvestimentoService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvestimentoResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(InvestimentoResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvestimentoResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody InvestimentoRequest req) {
        return InvestimentoResponse.from(service.criar(user.familiaId(), req));
    }

    @PutMapping("/{id}")
    public InvestimentoResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                          @Valid @RequestBody InvestimentoRequest req) {
        return InvestimentoResponse.from(service.atualizar(user.familiaId(), id, req));
    }

    @PutMapping("/{id}/valor-atual")
    public InvestimentoResponse atualizarValor(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                               @Valid @RequestBody ValorAtualRequest req) {
        return InvestimentoResponse.from(service.atualizarValor(user.familiaId(), id, req.valorAtual()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
