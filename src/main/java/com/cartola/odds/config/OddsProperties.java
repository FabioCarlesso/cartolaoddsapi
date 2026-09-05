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

    /**
     * TTL, em minutos, de uma resposta <em>degradada</em> (sem nenhum jogo) no cache
     * {@code odds}. Existe separado do TTL normal porque os dois erram para lados opostos:
     * guardar uma lista vazia pelo TTL cheio desliga o filtro de favoritos por uma hora por
     * causa de uma falha momentanea, e nao guardar nada faria cada requisicao repetir a
     * chamada — e uma resposta legitimamente vazia (fora de temporada) custa credito igual.
     */
    private int cacheTtlDegradadoMinutos = 10;

    /**
     * Intervalo minimo, em horas, entre chamadas de sondagem com o guardrail ativo. O saldo
     * so e reavaliado quando uma chamada acontece, entao sem sondagem o guardrail nunca
     * perceberia a virada de mes que renova a cota — ficaria travado ate um restart.
     */
    private int sondaIntervaloHoras = 24;
}
