package com.casacapital.backend.mesada;

import com.casacapital.backend.mesada.dto.AcessoDependenteRequest;
import com.casacapital.backend.mesada.dto.GastoMesadaRequest;
import com.casacapital.backend.mesada.dto.MesadaConfigRequest;
import com.casacapital.backend.mesada.dto.MesadaResponse;
import com.casacapital.backend.mesada.dto.MovimentoMesadaRequest;
import com.casacapital.backend.security.SecurityUser;
import com.casacapital.backend.usuario.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /api/mesada/minha* é a área do filho (perfil DEPENDENTE, escopada pelo membro do login);
 * o resto é dos pais (SecurityConfig).
 */
@RestController
@RequestMapping("/api/mesada")
public class MesadaController {

    private final MesadaService service;
    private final UsuarioRepository usuarios;

    public MesadaController(MesadaService service, UsuarioRepository usuarios) {
        this.service = service;
        this.usuarios = usuarios;
    }

    @GetMapping
    public List<MesadaResponse> listar(@AuthenticationPrincipal SecurityUser user) {
        return service.listar(user.familiaId());
    }

    @GetMapping("/{membroId}")
    public MesadaResponse detalhe(@AuthenticationPrincipal SecurityUser user, @PathVariable Long membroId) {
        return service.detalhe(user.familiaId(), membroId);
    }

    @PutMapping("/{membroId}")
    public MesadaResponse configurar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long membroId,
                                     @Valid @RequestBody MesadaConfigRequest req) {
        return service.configurar(user.familiaId(), membroId, req);
    }

    @DeleteMapping("/{membroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerConfiguracao(@AuthenticationPrincipal SecurityUser user, @PathVariable Long membroId) {
        service.removerConfiguracao(user.familiaId(), membroId);
    }

    @PostMapping("/{membroId}/movimentos")
    public MesadaResponse lancar(@AuthenticationPrincipal SecurityUser user, @PathVariable Long membroId,
                                 @Valid @RequestBody MovimentoMesadaRequest req) {
        String autor = usuarios.findById(user.usuarioId()).map(u -> u.getNome()).orElse(null);
        return service.lancar(user.familiaId(), membroId, req, autor);
    }

    @DeleteMapping("/{membroId}/movimentos/{movimentoId}")
    public MesadaResponse excluirMovimento(@AuthenticationPrincipal SecurityUser user, @PathVariable Long membroId,
                                           @PathVariable Long movimentoId) {
        return service.excluirMovimento(user.familiaId(), membroId, movimentoId);
    }

    @PutMapping("/{membroId}/acesso")
    public MesadaResponse definirAcesso(@AuthenticationPrincipal SecurityUser user, @PathVariable Long membroId,
                                        @Valid @RequestBody AcessoDependenteRequest req) {
        return service.definirAcesso(user.familiaId(), membroId, req);
    }

    @DeleteMapping("/{membroId}/acesso")
    public MesadaResponse removerAcesso(@AuthenticationPrincipal SecurityUser user, @PathVariable Long membroId) {
        return service.removerAcesso(user.familiaId(), membroId);
    }

    // ── Área do filho ───────────────────────────────────────────────────

    @GetMapping("/minha")
    public MesadaResponse minha(@AuthenticationPrincipal SecurityUser user) {
        return service.detalhe(user.familiaId(), user.membroId());
    }

    @PostMapping("/minha/gastos")
    public MesadaResponse gastar(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody GastoMesadaRequest req) {
        return service.gastoDoFilho(user.familiaId(), user.membroId(), req);
    }
}
