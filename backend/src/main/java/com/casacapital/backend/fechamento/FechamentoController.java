package com.casacapital.backend.fechamento;

import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.security.SecurityUser;
import com.casacapital.backend.usuario.Usuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

@RestController
@RequestMapping("/api/fechamento")
public class FechamentoController {

    private final FechamentoService service;
    private final FechamentoEmailService email;
    private final FamiliaRepository familias;
    private final UsuarioRepository usuarios;
    private final boolean emailConfigurado;

    public FechamentoController(FechamentoService service, FechamentoEmailService email, FamiliaRepository familias,
                                UsuarioRepository usuarios, @Value("${spring.mail.username:}") String remetente) {
        this.service = service;
        this.email = email;
        this.familias = familias;
        this.usuarios = usuarios;
        this.emailConfigurado = remetente != null && !remetente.isBlank();
    }

    /** Fechamento do mês ("AAAA-MM"); sem mês, o mês anterior ao atual. */
    @GetMapping
    public FechamentoResponse fechamento(@AuthenticationPrincipal SecurityUser user, @RequestParam(required = false) String mes) {
        return service.gerar(user.familiaId(), mes(mes));
    }

    @GetMapping("/preferencias")
    @Transactional(readOnly = true)
    public Map<String, Object> preferencias(@AuthenticationPrincipal SecurityUser user) {
        Familia f = familias.findById(user.familiaId()).orElseThrow();
        return Map.of("emailAtivo", f.isFechamentoEmail(), "emailConfigurado", emailConfigurado,
                "destinatarios", usuarios.findAllByFamiliaId(user.familiaId()).stream().map(Usuario::getEmail).toList());
    }

    @PutMapping("/preferencias")
    @Transactional
    public Map<String, Object> definir(@AuthenticationPrincipal SecurityUser user, @RequestBody Map<String, Boolean> corpo) {
        Familia f = familias.findById(user.familiaId()).orElseThrow();
        f.setFechamentoEmail(Boolean.TRUE.equals(corpo.get("emailAtivo")));
        return Map.of("emailAtivo", f.isFechamentoEmail());
    }

    /** Envia agora o fechamento do mês por e-mail (para testar). */
    @PostMapping("/enviar")
    public Map<String, Object> enviar(@AuthenticationPrincipal SecurityUser user, @RequestParam(required = false) String mes) {
        if (!emailConfigurado) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NAO_CONFIGURADO",
                    "O envio de e-mail não está configurado neste sistema (MAIL_USERNAME no configuracao.ini).");
        }
        Familia f = familias.findById(user.familiaId()).orElseThrow();
        try {
            return Map.of("enviados", email.enviar(f, mes(mes)));
        } catch (MailException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_FALHOU", "Não foi possível enviar o e-mail agora: " + e.getMessage());
        }
    }

    private static YearMonth mes(String texto) {
        if (texto == null || texto.isBlank()) return YearMonth.now().minusMonths(1);
        try {
            return YearMonth.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MES_INVALIDO", "Mês inválido (use AAAA-MM).");
        }
    }
}
