package com.cartola.odds.config;

import com.cartola.odds.model.OddsComOrigem;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.RequiredArgsConstructor;
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
 *  - odds        : respostas da The Odds API  (TTL configuravel via odds.api.cache-ttl-minutos,
 *                  padrao 60 min — odds de Brasileirao nao mudam a cada poucos minutos;
 *                  resposta sem jogos expira em odds.api.cache-ttl-degradado-minutos)
 *  - atletas     : /atletas/mercado           (TTL 15 min — mercado abre/fecha poucas vezes por dia)
 *  - clubes      : /clubes                    (TTL  1 hora — dados estaticos durante a temporada)
 *  - partidas    : /partidas                  (TTL 15 min — partidas da rodada sao fixas)
 *  - pontuados   : /atletas/pontuados         (TTL 15 min — historico muda apos cada rodada)
 *  - statusMercado: /mercado/status           (TTL  2 min — verificado com frequencia)
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    public static final String CACHE_ODDS           = "odds";
    public static final String CACHE_ATLETAS        = "atletas";
    public static final String CACHE_CLUBES         = "clubes";
    public static final String CACHE_PARTIDAS       = "partidas";
    public static final String CACHE_PONTUADOS      = "pontuados";
    public static final String CACHE_STATUS_MERCADO = "statusMercado";
    public static final String CACHE_CONFIGURACAO   = "configuracao";

    private final OddsProperties oddsProperties;

    @Bean
    public CacheManager cacheManager() {
        var manager = new CaffeineCacheManager(
            CACHE_ATLETAS,
            CACHE_CLUBES,
            CACHE_PARTIDAS,
            CACHE_PONTUADOS,
            CACHE_STATUS_MERCADO,
            CACHE_CONFIGURACAO
        );
        manager.setCaffeine(caffeine(10));
        // TTL proprio para 'odds': configuravel, e diferente para resposta boa e degradada.
        manager.registerCustomCache(CACHE_ODDS, cacheDeOdds());
        return manager;
    }

    /**
     * Configuracao base do Caffeine:
     *  - TTL informado (em minutos)
     *  - Tamanho maximo de 500 entradas por cache
     *  - Remocao apos escrita (write-based expiration)
     */
    private Caffeine<Object, Object> caffeine(long ttlMinutos) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutos, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats();
    }

    /**
     * O cache de odds expira por resultado, e nao por tempo fixo, porque os dois casos erram
     * para lados opostos. Uma resposta com jogos vale o TTL cheio (padrao 60 min): odds de
     * Brasileirao nao mudam a cada poucos minutos, e cada busca nova custa credito. Uma
     * resposta <em>sem</em> jogos — provedor fora do ar sem snapshot, ou fora de temporada —
     * guardada pelo mesmo TTL desligaria o filtro de favoritos por uma hora por causa de uma
     * falha momentanea; guardada por poucos minutos (padrao 10), a recuperacao e rapida sem
     * que cada requisicao vire uma chamada paga.
     */
    private Cache<Object, Object> cacheDeOdds() {
        return Caffeine.newBuilder()
                .expireAfter(new TtlPorResultado(
                        oddsProperties.getCacheTtlMinutos(),
                        oddsProperties.getCacheTtlDegradadoMinutos()))
                .maximumSize(500)
                .recordStats()
                .build();
    }

    /** TTL curto para resposta de odds sem nenhum jogo, TTL cheio para as demais. */
    private record TtlPorResultado(long ttlMinutos, long ttlDegradadoMinutos) implements Expiry<Object, Object> {

        @Override
        public long expireAfterCreate(Object chave, Object valor, long agora) {
            return duracaoNanos(valor);
        }

        @Override
        public long expireAfterUpdate(Object chave, Object valor, long agora, long duracaoAtual) {
            return duracaoNanos(valor);
        }

        @Override
        public long expireAfterRead(Object chave, Object valor, long agora, long duracaoAtual) {
            return duracaoAtual;
        }

        private long duracaoNanos(Object valor) {
            boolean degradado = valor instanceof OddsComOrigem odds && odds.vazio();
            return TimeUnit.MINUTES.toNanos(degradado ? ttlDegradadoMinutos : ttlMinutos);
        }
    }
}
