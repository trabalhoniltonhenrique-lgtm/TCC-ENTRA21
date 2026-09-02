package com.casacapital.backend.alerta;

import com.casacapital.backend.alerta.dto.AlertaLimiteRequest;
import com.casacapital.backend.alerta.dto.AlertaLimiteResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
public class AlertaLimiteController {

    private final AlertaLimiteService service;

    public AlertaLimiteController(AlertaLimiteService service) {
        this.service = service;
    }

    @GetMapping
    public List<AlertaLimiteResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(AlertaLimiteResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertaLimiteResponse salvar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody AlertaLimiteRequest req) {
        return AlertaLimiteResponse.from(service.salvar(user.familiaId(), req));
    }

    @DeleteMapping("/{categoria}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable String categoria) {
        service.excluir(user.familiaId(), categoria);
    }
}
