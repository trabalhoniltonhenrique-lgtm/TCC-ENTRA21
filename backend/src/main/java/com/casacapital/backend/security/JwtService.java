package com.casacapital.backend.security;

import com.casacapital.backend.familia.PlanoFamilia;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String gerarToken(Long usuarioId, Long familiaId, String email, PlanoFamilia plano) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(properties.expirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("familiaId", familiaId)
                .claim("email", email)
                .claim("plano", plano.name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(key)
                .compact();
    }

    public Optional<SecurityUser> validarEExtrair(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long usuarioId = Long.valueOf(claims.getSubject());
            Long familiaId = claims.get("familiaId", Long.class);
            String email = claims.get("email", String.class);
            PlanoFamilia plano = PlanoFamilia.valueOf(claims.get("plano", String.class));

            return Optional.of(new SecurityUser(usuarioId, familiaId, email, plano));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
