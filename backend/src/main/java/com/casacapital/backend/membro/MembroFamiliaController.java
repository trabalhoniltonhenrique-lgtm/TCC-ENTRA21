package com.casacapital.backend.membro;

import com.casacapital.backend.membro.dto.MembroRequest;
import com.casacapital.backend.membro.dto.MembroResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/membros")
public class MembroFamiliaController {

    private final MembroFamiliaService service;

    public MembroFamiliaController(MembroFamiliaService service) {
        this.service = service;
    }

    @GetMapping
    public List<MembroResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(MembroResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembroResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody MembroRequest req) {
        return MembroResponse.from(service.criar(user.familiaId(), req));
    }

    @PutMapping("/{id}")
    public MembroResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                     @Valid @RequestBody MembroRequest req) {
        return MembroResponse.from(service.atualizar(user.familiaId(), id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
