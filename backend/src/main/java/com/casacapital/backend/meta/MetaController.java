package com.casacapital.backend.meta;

import com.casacapital.backend.investimento.InvestimentoService;
import com.casacapital.backend.meta.dto.AporteRequest;
import com.casacapital.backend.meta.dto.MetaRequest;
import com.casacapital.backend.meta.dto.MetaResponse;
import com.casacapital.backend.security.SecurityUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metas")
public class MetaController {

    private final MetaService service;
    private final InvestimentoService investimentos;

    public MetaController(MetaService service, InvestimentoService investimentos) {
        this.service = service;
        this.investimentos = investimentos;
    }

    /** Monta a resposta somando o valor atual dos investimentos ligados à meta. */
    private MetaResponse resposta(Long familiaId, Meta m) {
        return MetaResponse.from(m, investimentos.totalPorMeta(familiaId).get(m.getId()));
    }

    @GetMapping
    public List<MetaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        Map<Long, BigDecimal> investido = investimentos.totalPorMeta(user.familiaId());
        return service.listar(user.familiaId()).stream().map(m -> MetaResponse.from(m, investido.get(m.getId()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetaResponse criar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody MetaRequest req) {
        return resposta(user.familiaId(), service.criar(user.familiaId(), req));
    }

    @PutMapping("/{id}")
    public MetaResponse atualizar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                   @Valid @RequestBody MetaRequest req) {
        return resposta(user.familiaId(), service.atualizar(user.familiaId(), id, req));
    }

    @PostMapping("/{id}/aporte")
    public MetaResponse aportar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id,
                                 @Valid @RequestBody AporteRequest req) {
        return resposta(user.familiaId(), service.aportar(user.familiaId(), id, req.valor()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal SecurityUser user, @PathVariable Long id) {
        service.excluir(user.familiaId(), id);
    }
}
