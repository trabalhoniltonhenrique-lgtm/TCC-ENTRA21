package com.casacapital.backend.impostorenda;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/imposto-renda")
public class ImpostoRendaController {

    private final ImpostoRendaService service;

    public ImpostoRendaController(ImpostoRendaService service) {
        this.service = service;
    }

    /** Dados do ano-calendário (sem ano: o ano passado, que é o declarado no ano atual). */
    @GetMapping
    public ImpostoRendaResponse gerar(@AuthenticationPrincipal SecurityUser user, @RequestParam(required = false) Integer ano) {
        int a = ano != null ? ano : LocalDate.now().getYear() - 1;
        if (a < 2000 || a > LocalDate.now().getYear()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ANO_INVALIDO", "Ano inválido.");
        }
        return service.gerar(user.familiaId(), a);
    }
}
