package com.cartola.odds.config;

import com.cartola.odds.model.OddsComOrigem;
import com.cartola.odds.model.response.OddsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "odds.api.key=TEST",
    "odds.api.base-url=https://api.the-odds-api.com/v4",
    "cartola.api.base-url=https://api.cartola.globo.com",
    "odds.api.cache-ttl-minutos=60",
    "odds.api.cache-ttl-degradado-minutos=10"
})
@DisplayName("CacheConfig")
class CacheConfigTest {

    @Autowired CacheManager cacheManager;

    @Test
    @DisplayName("deve usar CaffeineCacheManager como implementacao")
    void deveUsarCaffeine() {
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
    }

    @Test
    @DisplayName("cache de odds deve guardar resposta com jogos pelo TTL cheio configurado")
    void oddsComJogosDeveUsarTtlCheio() {
        assertThat(ttlDeOddsEmMinutos(new OddsResponse())).isEqualTo(60L);
    }

    @Test
    @DisplayName("cache de odds deve guardar resposta sem jogos apenas pelo TTL degradado")
    void oddsVaziaDeveUsarTtlDegradado() {
        // Uma falha momentanea do provedor nao pode desligar o filtro de favoritos pelo TTL
        // cheio; guardar por poucos minutos recupera rapido sem virar chamada paga por request.
        assertThat(ttlDeOddsEmMinutos()).isEqualTo(10L);
    }

    /** TTL efetivo, em minutos, que o cache de odds atribui a uma resposta com estes jogos. */
    private long ttlDeOddsEmMinutos(OddsResponse... jogos) {
        var cacheNativo = ((CaffeineCache) cacheManager.getCache(CacheConfig.CACHE_ODDS)).getNativeCache();
        cacheNativo.put("chave-de-teste", OddsComOrigem.aoVivo(List.of(jogos)));

        long nanos = cacheNativo.policy().expireVariably().orElseThrow()
                .getExpiresAfter("chave-de-teste", TimeUnit.NANOSECONDS).orElseThrow();
        // Arredonda: o valor lido e o tempo restante, ja alguns microssegundos menor que o TTL.
        return Math.round(nanos / (double) TimeUnit.MINUTES.toNanos(1));
    }

    @Test
    @DisplayName("deve ter os 6 caches configurados")
    void deveTerTodosOsCaches() {
        assertThat(cacheManager.getCache(CacheConfig.CACHE_ODDS)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_ATLETAS)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_CLUBES)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_PARTIDAS)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_PONTUADOS)).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_STATUS_MERCADO)).isNotNull();
    }
}
