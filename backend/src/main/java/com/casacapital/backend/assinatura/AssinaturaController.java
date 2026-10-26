package com.casacapital.backend.assinatura;

import com.casacapital.backend.security.SecurityUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/assinaturas")
public class AssinaturaController {

    private final AssinaturaService service;

    public AssinaturaController(AssinaturaService service) {
        this.service = service;
    }

    @GetMapping
    public AssinaturasResponse listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId(), LocalDate.now());
    }

    /** "Não é assinatura": { "chave": "SUPERMERCADO SCHUTZE" } */
    @PostMapping("/ignoradas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ignorar(@AuthenticationPrincipal SecurityUser user, @RequestBody Map<String, String> corpo) {
        service.ignorar(user.familiaId(), corpo.get("chave"));
    }

    @DeleteMapping("/ignoradas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desfazer(@AuthenticationPrincipal SecurityUser user, @RequestParam String chave) {
        service.desfazerIgnorar(user.familiaId(), chave);
    }
}
