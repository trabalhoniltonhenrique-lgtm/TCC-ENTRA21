package com.casacapital.backend.historicoprecos;

import com.casacapital.backend.historicoprecos.dto.ComparacaoMercadosResponse;
import com.casacapital.backend.security.SecurityUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mercados")
public class ComparacaoMercadosController {

    private final ComparacaoMercadosService service;

    public ComparacaoMercadosController(ComparacaoMercadosService service) {
        this.service = service;
    }

    @GetMapping("/comparacao")
    public ComparacaoMercadosResponse comparar(@AuthenticationPrincipal SecurityUser user) {
        return service.comparar(user.familiaId());
    }
}
