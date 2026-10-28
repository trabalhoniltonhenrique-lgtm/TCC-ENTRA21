package com.casacapital.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // O filho (dependente) só alcança a própria mesada, as próprias tarefas e compromissos e a lista de
                        // compras; os controllers ainda escopam pelo membro do login. A foto da família ele só vê.
                        .requestMatchers(HttpMethod.GET, "/api/familia/me", "/api/familia/foto", "/api/tarefas", "/api/compras",
                                "/api/compromissos", "/api/compromissos/alertas")
                            .hasAnyRole("RESPONSAVEL", "DEPENDENTE")
                        .requestMatchers(HttpMethod.POST, "/api/tarefas/*/concluir", "/api/compras",
                                "/api/compromissos/*/ciente", "/api/compromissos/*/adiar")
                            .hasAnyRole("RESPONSAVEL", "DEPENDENTE")
                        .requestMatchers("/api/mesada/minha", "/api/mesada/minha/**").hasRole("DEPENDENTE")
                        .requestMatchers("/api/**").hasRole("RESPONSAVEL")
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
