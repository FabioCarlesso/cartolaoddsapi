package com.cartola.odds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Segredo HMAC usado para assinar os tokens. Sem valor padrao versionado:
     * em producao e obrigatorio (JWT_SECRET) e fora dela, quando ausente, o
     * {@code JwtService} gera uma chave efemera a cada boot.
     */
    private String secret;

    /** Validade do access token em milissegundos (padrao: 24 horas). */
    private long expirationMs = 86_400_000L;
}
