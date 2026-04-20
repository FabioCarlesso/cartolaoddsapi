package com.cartola.odds.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "odds.api.key=TEST",
    "odds.api.base-url=https://api.the-odds-api.com/v4",
    "cartola.api.base-url=https://api.cartola.globo.com"
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
