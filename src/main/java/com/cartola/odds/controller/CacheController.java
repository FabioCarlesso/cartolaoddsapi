package com.cartola.odds.controller;

import com.cartola.odds.config.CacheConfig;
import com.cartola.odds.controller.api.CacheApi;
import com.cartola.odds.model.response.CacheResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CacheController implements CacheApi {

    private static final List<String> CACHES_VALIDOS = List.of(
        CacheConfig.CACHE_ODDS,
        CacheConfig.CACHE_ATLETAS,
        CacheConfig.CACHE_CLUBES,
        CacheConfig.CACHE_PARTIDAS,
        CacheConfig.CACHE_PONTUADOS,
        CacheConfig.CACHE_STATUS_MERCADO
    );

    private final CacheManager cacheManager;

    @Override
    public ResponseEntity<CacheResponse> invalidarTodos() {
        log.info("DELETE /api/cache - Invalidando todos os caches...");
        CACHES_VALIDOS.forEach(this::evictCache);
        log.info("Todos os caches invalidados: {}", CACHES_VALIDOS);
        return ResponseEntity.ok(CacheResponse.of(CACHES_VALIDOS, "Todos os caches invalidados com sucesso."));
    }

    @Override
    public ResponseEntity<CacheResponse> invalidarPorNome(String nome) {
        log.info("DELETE /api/cache/{} - Invalidando cache...", nome);
        if (!CACHES_VALIDOS.contains(nome)) {
            throw new IllegalArgumentException(
                "Cache '" + nome + "' nao encontrado. Caches validos: " + CACHES_VALIDOS
            );
        }
        evictCache(nome);
        log.info("Cache '{}' invalidado.", nome);
        return ResponseEntity.ok(CacheResponse.of(List.of(nome), "Cache '" + nome + "' invalidado com sucesso."));
    }

    private void evictCache(String nome) {
        var cache = cacheManager.getCache(nome);
        if (cache != null) {
            cache.clear();
        }
    }
}
