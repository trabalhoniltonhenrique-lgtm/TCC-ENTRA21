package com.casacapital.backend.compromisso;

import com.casacapital.backend.compromisso.dto.CompromissoRequest;
import com.casacapital.backend.compromisso.dto.CompromissoResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** O filho (dependente) vê e confirma os alertas dos compromissos dele; criar e alterar é dos pais (SecurityConfig). */
@RestController
@RequestMapping("/api/compromissos")
public class CompromissoController {

    private final CompromissoService service;

    public CompromissoController(CompromissoService service) {
        this.service = service;
    }

    private static Long membroDoFilho(SecurityUser user) {
        return user.dependente() ? user.membroId() : null;
    }

    @GetMapping
    public List<CompromissoResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId(), membroDoFilho(user));
    }

    /** Alertas que devem aparecer agora (o navegador consulta a cada 30 segundos). */
    @GetMapping("/alertas")
    public List<CompromissoResponse> alertas(@AuthenticationPrincipal SecurityUser user) {
        return service.alertas(user.familiaId(), membroDoFilho(user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompromissoResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody CompromissoRequest req) {
        return service.criar(user.familiaId(), req);
    }

    @PutMapping("/{id}")
    public CompromissoResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                         @Valid @RequestBody CompromissoRequest req) {
        return service.atualizar(user.familiaId(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }

    @PostMapping("/{id}/ciente")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ciente(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.ciente(user.familiaId(), id, membroDoFilho(user));
    }

    @PostMapping("/{id}/adiar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adiar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                      @RequestParam(defaultValue = "5") int minutos) {
        service.adiar(user.familiaId(), id, minutos, membroDoFilho(user));
    }
}
