package com.casacapital.backend.compra;

import com.casacapital.backend.compra.dto.ComprarItemRequest;
import com.casacapital.backend.compra.dto.CompraRequest;
import com.casacapital.backend.compra.dto.CompraResponse;
import com.casacapital.backend.dispensa.dto.ItemDispensaResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final CompraService service;

    public CompraController(CompraService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompraResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId()).stream().map(CompraResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody CompraRequest req) {
        return CompraResponse.from(service.criar(user.familiaId(), req));
    }

    @PostMapping("/{id}/comprar")
    public ItemDispensaResponse comprar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                         @RequestBody(required = false) ComprarItemRequest req) {
        var preco = req != null ? req.precoUnitario() : null;
        return ItemDispensaResponse.from(service.comprarItem(user.familiaId(), id, preco));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
