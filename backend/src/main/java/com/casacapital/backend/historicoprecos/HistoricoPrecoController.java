package com.casacapital.backend.historicoprecos;

import com.casacapital.backend.historicoprecos.dto.ProdutoHistoricoResponse;
import com.casacapital.backend.security.SecurityUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/historico-precos")
public class HistoricoPrecoController {

    private final HistoricoPrecoService service;

    public HistoricoPrecoController(HistoricoPrecoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProdutoHistoricoResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId());
    }

    @GetMapping("/{produtoChave}")
    public ProdutoHistoricoResponse buscar(@AuthenticationPrincipal SecurityUser user, @PathVariable String produtoChave) {
        return service.buscar(user.familiaId(), produtoChave);
    }
}
