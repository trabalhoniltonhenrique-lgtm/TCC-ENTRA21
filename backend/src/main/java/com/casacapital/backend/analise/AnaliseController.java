package com.casacapital.backend.analise;

import com.casacapital.backend.analise.dto.AnaliseResumoResponse;
import com.casacapital.backend.security.SecurityUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analises")
public class AnaliseController {

    private final AnaliseService analiseService;

    public AnaliseController(AnaliseService analiseService) {
        this.analiseService = analiseService;
    }

    @GetMapping("/resumo")
    public AnaliseResumoResponse resumo(@AuthenticationPrincipal SecurityUser user) {
        return analiseService.resumo(user.familiaId());
    }
}
