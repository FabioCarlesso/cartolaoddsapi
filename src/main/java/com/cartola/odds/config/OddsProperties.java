package com.cartola.odds.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
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
    @Min(1)
    private int minRequestsRemaining = 50;

    /**
     * TTL do cache {@code odds} em minutos. Odds de Brasileirao nao mudam a cada poucos
     * minutos, e um TTL curto multiplica o consumo de cota sem ganho real.
     */
    @Min(1)
    private int cacheTtlMinutos = 60;

    /**
     * TTL, em minutos, de uma resposta <em>degradada</em> (sem nenhum jogo) no cache
     * {@code odds}. Existe separado do TTL normal porque os dois erram para lados opostos:
     * guardar uma lista vazia pelo TTL cheio desliga o filtro de favoritos por uma hora por
     * causa de uma falha momentanea, e nao guardar nada faria cada requisicao repetir a
     * chamada — e uma resposta legitimamente vazia (fora de temporada) custa credito igual.
     */
    @Min(1)
    private int cacheTtlDegradadoMinutos = 10;

    /**
     * Intervalo minimo, em horas, entre chamadas de sondagem com o guardrail ativo. O saldo
     * so e reavaliado quando uma chamada acontece, entao sem sondagem o guardrail nunca
     * perceberia a virada de mes que renova a cota — ficaria travado ate um restart.
     *
     * <p>Minimo de 1: em {@code 0} toda requisicao viraria sondagem e o guardrail deixaria de
     * existir na pratica, gastando exatamente a cota que ele foi feito para preservar.
     */
    @Min(1)
    private int sondaIntervaloHoras = 24;

    /**
     * O TTL degradado e um <em>piso</em> dentro do TTL cheio, entao precisa caber nele. Sao
     * duas variaveis de ambiente independentes ({@code ODDS_API_CACHE_TTL_MINUTOS} e
     * {@code ODDS_API_CACHE_TTL_DEGRADADO_MINUTOS}), e invertidas nao produzem so um TTL
     * estranho: o calculo de validade do cache passa a receber um intervalo de cabeca para
     * baixo. Recusar no boot, com o nome das duas propriedades, custa um restart; aceitar
     * custaria erro em toda requisicao de odds, ja em producao.
     */
    @AssertTrue(message = "odds.api.cache-ttl-degradado-minutos deve ser menor ou igual a odds.api.cache-ttl-minutos")
    public boolean isTtlDegradadoDentroDoTtlCheio() {
        return cacheTtlDegradadoMinutos <= cacheTtlMinutos;
    }
}
