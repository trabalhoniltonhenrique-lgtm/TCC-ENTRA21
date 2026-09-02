package com.casacapital.backend.conta;

import com.casacapital.backend.conta.dto.ContaRequest;
import com.casacapital.backend.conta.dto.ContaResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contas")
public class ContaController {

    private final ContaService service;

    public ContaController(ContaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(ContaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ContaResponse> criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody ContaRequest req) {
        return service.criar(user.familiaId(), req).stream().map(ContaResponse::from).toList();
    }

    @PutMapping("/{id}")
    public ContaResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                    @Valid @RequestBody ContaRequest req) {
        return ContaResponse.from(service.atualizar(user.familiaId(), id, req));
    }

    @PostMapping("/{id}/marcar-pago")
    public ContaResponse marcarPago(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        return ContaResponse.from(service.marcarPago(user.familiaId(), id));
    }

    @PostMapping("/{id}/reabrir")
    public ContaResponse reabrir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        return ContaResponse.from(service.reabrir(user.familiaId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                         @RequestParam(defaultValue = "false") boolean apagarGrupo) {
        service.excluir(user.familiaId(), id, apagarGrupo);
    }
}
