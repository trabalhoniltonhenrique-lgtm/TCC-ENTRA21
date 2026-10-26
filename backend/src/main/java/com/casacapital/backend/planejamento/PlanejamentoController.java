package com.casacapital.backend.planejamento;

import com.casacapital.backend.planejamento.dto.FluxoCaixaResponse;
import com.casacapital.backend.planejamento.dto.PatrimonioResponse;
import com.casacapital.backend.security.SecurityUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/planejamento")
public class PlanejamentoController {

    private final PlanejamentoService service;

    public PlanejamentoController(PlanejamentoService service) {
        this.service = service;
    }

    @GetMapping("/fluxo-caixa")
    public FluxoCaixaResponse fluxoCaixa(@AuthenticationPrincipal SecurityUser user,
                                         @RequestParam(defaultValue = "6") int meses) {
        return service.fluxoCaixa(user.familiaId(), Math.max(1, Math.min(meses, 12)), LocalDate.now());
    }

    @GetMapping("/patrimonio")
    public PatrimonioResponse patrimonio(@AuthenticationPrincipal SecurityUser user) {
        return service.patrimonio(user.familiaId(), LocalDate.now());
    }
}
