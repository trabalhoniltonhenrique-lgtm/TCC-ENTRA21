package com.casacapital.backend.orcamento;

import com.casacapital.backend.orcamento.dto.OrcamentoItemRequest;
import com.casacapital.backend.orcamento.dto.OrcamentoItemResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orcamentos")
public class OrcamentoMensalController {

    private final OrcamentoMensalService service;

    public OrcamentoMensalController(OrcamentoMensalService service) {
        this.service = service;
    }

    @GetMapping("/{mesAno}")
    public List<OrcamentoItemResponse> listar(@AuthenticationPrincipal SecurityUser user, @PathVariable String mesAno) {
        return service.listar(user.familiaId(), mesAno).stream().map(OrcamentoItemResponse::from).toList();
    }

    @PutMapping("/{mesAno}")
    public OrcamentoItemResponse definir(@AuthenticationPrincipal SecurityUser user, @PathVariable String mesAno,
                                          @Valid @RequestBody OrcamentoItemRequest req) {
        return OrcamentoItemResponse.from(service.definir(user.familiaId(), mesAno, req));
    }

    @DeleteMapping("/{mesAno}/{categoria}")
    public void remover(@AuthenticationPrincipal SecurityUser user, @PathVariable String mesAno,
                         @PathVariable String categoria) {
        service.remover(user.familiaId(), mesAno, categoria);
    }

    @PostMapping("/{mesAno}/copiar-mes-anterior")
    public List<OrcamentoItemResponse> copiarMesAnterior(@AuthenticationPrincipal SecurityUser user,
                                                           @PathVariable String mesAno) {
        return service.copiarMesAnterior(user.familiaId(), mesAno).stream().map(OrcamentoItemResponse::from).toList();
    }
}
