package com.casacapital.backend.dispensa;

import com.casacapital.backend.dispensa.dto.ItemDispensaRequest;
import com.casacapital.backend.dispensa.dto.ItemDispensaResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dispensa")
public class DispensaController {

    private final DispensaService service;

    public DispensaController(DispensaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ItemDispensaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(ItemDispensaResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDispensaResponse adicionar(@AuthenticationPrincipal SecurityUser user,
                                           @Valid @RequestBody ItemDispensaRequest req) {
        return ItemDispensaResponse.from(service.adicionar(user.familiaId(), req));
    }

    @PostMapping("/{id}/qtd")
    public ItemDispensaResponse ajustarQtd(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                            @RequestParam int delta) {
        return ItemDispensaResponse.from(service.atualizarQtd(user.familiaId(), id, delta));
    }

    @PostMapping("/{id}/mover-para-compras")
    public void moverParaCompras(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.moverParaCompras(user.familiaId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
