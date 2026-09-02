package com.casacapital.backend.receita;

import com.casacapital.backend.receita.dto.ReceitaRequest;
import com.casacapital.backend.receita.dto.ReceitaResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/receitas")
public class ReceitaController {

    private final ReceitaService service;

    public ReceitaController(ReceitaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReceitaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(ReceitaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReceitaResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody ReceitaRequest req) {
        return ReceitaResponse.from(service.criar(user.familiaId(), req));
    }

    @PutMapping("/{id}")
    public ReceitaResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                      @Valid @RequestBody ReceitaRequest req) {
        return ReceitaResponse.from(service.atualizar(user.familiaId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
