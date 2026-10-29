package com.casacapital.backend.auth;

import com.casacapital.backend.auth.dto.AuthResponse;
import com.casacapital.backend.auth.dto.LoginRequest;
import com.casacapital.backend.auth.dto.RegistrarRequest;
import com.casacapital.backend.common.ApiException;
import com.casacapital.backend.common.BadCredentialsException;
import com.casacapital.backend.common.EmailJaCadastradoException;
import com.casacapital.backend.common.TokenInvalidoException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.familia.dto.FamiliaResponse;
import com.casacapital.backend.security.JwtService;
import com.casacapital.backend.usuario.PerfilUsuario;
import com.casacapital.backend.usuario.Usuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int EXPIRACAO_TOKEN_RESET_MINUTOS = 30;

    private final FamiliaRepository familiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(FamiliaRepository familiaRepository,
                        UsuarioRepository usuarioRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        MailService mailService) {
        this.familiaRepository = familiaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.mailService = mailService;
    }

    @Transactional
    public AuthResponse registrar(RegistrarRequest req) {
        if (usuarioRepository.existsByEmailIgnoreCase(req.email())) {
            throw new EmailJaCadastradoException();
        }

        Familia familia = new Familia(req.nomeFamilia());
        familia.setResponsavel(req.nome());
        familiaRepository.save(familia);

        Usuario usuario = new Usuario(familia, req.nome(), req.email().toLowerCase(),
                passwordEncoder.encode(req.senha()));
        usuarioRepository.save(usuario);

        String token = jwtService.gerarToken(usuario.getId(), familia.getId(), usuario.getEmail(), familia.getPlano());
        return new AuthResponse(token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                FamiliaResponse.from(familia), PerfilUsuario.RESPONSAVEL.name(), null);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(BadCredentialsException::new);

        if (!passwordEncoder.matches(req.senha(), usuario.getSenhaHash())) {
            throw new BadCredentialsException();
        }

        Familia familia = usuario.getFamilia();
        Long membroId = usuario.getMembro() != null ? usuario.getMembro().getId() : null;
        String token = jwtService.gerarToken(usuario.getId(), familia.getId(), usuario.getEmail(), familia.getPlano(),
                usuario.getPerfil(), membroId);
        return new AuthResponse(token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                FamiliaResponse.from(familia), usuario.getPerfil().name(), membroId);
    }

    @Transactional
    public void esqueciSenha(String email) {
        // Sempre retorna sem erro, mesmo se o e-mail não existir, para não revelar quais
        // e-mails estão cadastrados na base (evita enumeração de contas).
        // o login do filho não é um e-mail: a senha dele é trocada pelos pais na tela Mesada
        usuarioRepository.findByEmailIgnoreCase(email)
                .filter(usuario -> usuario.getPerfil() == PerfilUsuario.RESPONSAVEL)
                .ifPresent(usuario -> {
            String token = gerarTokenAleatorio();
            Instant expiraEm = Instant.now().plus(EXPIRACAO_TOKEN_RESET_MINUTOS, ChronoUnit.MINUTES);
            passwordResetTokenRepository.save(new PasswordResetToken(usuario, token, expiraEm));
            try {
                mailService.enviarEmailRedefinicaoSenha(usuario.getEmail(), usuario.getNome(), token);
            } catch (MailException e) {
                // Gmail fora do ar, sem internet ou senha de app trocada: avisa em vez de "erro inesperado"
                log.error("Falha ao enviar o e-mail de redefinição de senha", e);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_INDISPONIVEL",
                        "Não foi possível enviar o e-mail agora. Confira a internet do computador da casa e tente de novo.");
            }
        });
    }

    @Transactional
    public void redefinirSenha(String token, String novaSenha) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .filter(PasswordResetToken::isValido)
                .orElseThrow(TokenInvalidoException::new);

        resetToken.getUsuario().setSenhaHash(passwordEncoder.encode(novaSenha));
        resetToken.setUsedAt(Instant.now());
    }

    private String gerarTokenAleatorio() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
