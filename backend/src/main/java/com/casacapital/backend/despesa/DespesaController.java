package com.casacapital.backend.despesa;

import com.casacapital.backend.despesa.dto.DespesaRequest;
import com.casacapital.backend.despesa.dto.DespesaResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/despesas")
public class DespesaController {

    private final DespesaService service;

    public DespesaController(DespesaService service) {
        this.service = service;
    }

    @GetMapping
    public List<DespesaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(DespesaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DespesaResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody DespesaRequest req) {
        return DespesaResponse.from(service.criar(user.familiaId(), req));
    }

    @PutMapping("/{id}")
    public DespesaResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                      @Valid @RequestBody DespesaRequest req) {
        return DespesaResponse.from(service.atualizar(user.familiaId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
