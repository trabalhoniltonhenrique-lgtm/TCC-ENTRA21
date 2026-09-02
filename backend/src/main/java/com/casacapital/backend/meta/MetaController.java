package com.casacapital.backend.meta;

import com.casacapital.backend.meta.dto.AporteRequest;
import com.casacapital.backend.meta.dto.MetaRequest;
import com.casacapital.backend.meta.dto.MetaResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metas")
public class MetaController {

    private final MetaService service;

    public MetaController(MetaService service) {
        this.service = service;
    }

    @GetMapping
    public List<MetaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(MetaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetaResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody MetaRequest req) {
        return MetaResponse.from(service.criar(user.familiaId(), req));
    }

    @PutMapping("/{id}")
    public MetaResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                   @Valid @RequestBody MetaRequest req) {
        return MetaResponse.from(service.atualizar(user.familiaId(), id, req));
    }

    @PostMapping("/{id}/aporte")
    public MetaResponse aportar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                 @Valid @RequestBody AporteRequest req) {
        return MetaResponse.from(service.aportar(user.familiaId(), id, req.valor()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
