package com.cartola.odds.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuracao de cache em memoria com Caffeine.
 *
 * Caches definidos:
 *  - odds        : respostas da The Odds API  (TTL 10 min — odds mudam pouco durante o dia)
 *  - atletas     : /atletas/mercado           (TTL 15 min — mercado abre/fecha poucas vezes por dia)
 *  - clubes      : /clubes                    (TTL  1 hora — dados estaticos durante a temporada)
 *  - partidas    : /partidas                  (TTL 15 min — partidas da rodada sao fixas)
 *  - pontuados   : /atletas/pontuados         (TTL 15 min — historico muda apos cada rodada)
 *  - statusMercado: /mercado/status           (TTL  2 min — verificado com frequencia)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_ODDS           = "odds";
    public static final String CACHE_ATLETAS        = "atletas";
    public static final String CACHE_CLUBES         = "clubes";
    public static final String CACHE_PARTIDAS       = "partidas";
    public static final String CACHE_PONTUADOS      = "pontuados";
    public static final String CACHE_STATUS_MERCADO = "statusMercado";
    public static final String CACHE_CONFIGURACAO   = "configuracao";

    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager(
            CACHE_ODDS,
            CACHE_ATLETAS,
            CACHE_CLUBES,
            CACHE_PARTIDAS,
            CACHE_PONTUADOS,
            CACHE_STATUS_MERCADO,
            CACHE_CONFIGURACAO
        );
        manager.setCaffeine(caffeine());
        return manager;
    }

    /**
     * Configuracao base do Caffeine:
     *  - TTL de 10 minutos (sobrescrito por cache especifico quando necessario)
     *  - Tamanho maximo de 500 entradas por cache
     *  - Remocao apos escrita (write-based expiration)
     */
    private Caffeine<Object, Object> caffeine() {
        return Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats();
    }
}
