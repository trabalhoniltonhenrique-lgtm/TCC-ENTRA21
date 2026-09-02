package com.casacapital.backend.tarefa;

import com.casacapital.backend.security.SecurityUser;
import com.casacapital.backend.tarefa.dto.TarefaRequest;
import com.casacapital.backend.tarefa.dto.TarefaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tarefas")
public class TarefaController {

    private final TarefaService service;

    public TarefaController(TarefaService service) {
        this.service = service;
    }

    @GetMapping
    public List<TarefaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(TarefaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarefaResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody TarefaRequest req) {
        return TarefaResponse.from(service.criar(user.familiaId(), req));
    }

    @PutMapping("/{id}")
    public TarefaResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                     @Valid @RequestBody TarefaRequest req) {
        return TarefaResponse.from(service.atualizar(user.familiaId(), id, req));
    }

    @PostMapping("/{id}/concluir")
    public TarefaResponse concluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        return TarefaResponse.from(service.concluir(user.familiaId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
