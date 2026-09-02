package com.casacapital.backend.auth;

import com.casacapital.backend.auth.dto.AuthResponse;
import com.casacapital.backend.auth.dto.LoginRequest;
import com.casacapital.backend.auth.dto.RegistrarRequest;
import com.casacapital.backend.common.BadCredentialsException;
import com.casacapital.backend.common.EmailJaCadastradoException;
import com.casacapital.backend.familia.Familia;
import com.casacapital.backend.familia.FamiliaRepository;
import com.casacapital.backend.familia.dto.FamiliaResponse;
import com.casacapital.backend.security.JwtService;
import com.casacapital.backend.usuario.Usuario;
import com.casacapital.backend.usuario.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final FamiliaRepository familiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(FamiliaRepository familiaRepository,
                        UsuarioRepository usuarioRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.familiaRepository = familiaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
                FamiliaResponse.from(familia));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(req.email())
                .orElseThrow(BadCredentialsException::new);

        if (!passwordEncoder.matches(req.senha(), usuario.getSenhaHash())) {
            throw new BadCredentialsException();
        }

        Familia familia = usuario.getFamilia();
        String token = jwtService.gerarToken(usuario.getId(), familia.getId(), usuario.getEmail(), familia.getPlano());
        return new AuthResponse(token, usuario.getId(), usuario.getNome(), usuario.getEmail(),
                FamiliaResponse.from(familia));
    }
}
