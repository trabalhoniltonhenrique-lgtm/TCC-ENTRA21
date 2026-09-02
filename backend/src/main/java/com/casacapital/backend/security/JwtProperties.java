package com.casacapital.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "casacapital.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
