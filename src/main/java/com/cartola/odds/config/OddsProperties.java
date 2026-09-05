package com.cartola.odds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "odds.api")
public class OddsProperties {

    private String key;
    private String baseUrl;
    private String sport;
    private String regions;
    private String markets;
    private int timeout = 10000;

    /**
     * Abaixo deste saldo restante de requisicoes (lido do header {@code x-requests-remaining}
     * da The Odds API), o {@code OddsClient} para de chamar o provedor e passa a servir a
     * ultima resposta conhecida, persistida em {@code odds_snapshot}.
     */
    private int minRequestsRemaining = 50;

    /**
     * TTL do cache {@code odds} em minutos. Odds de Brasileirao nao mudam a cada poucos
     * minutos, e um TTL curto multiplica o consumo de cota sem ganho real.
     */
    private int cacheTtlMinutos = 60;
}
