package com.cartola.odds.controller;

import com.cartola.odds.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CacheController.class)
// Filtros de seguranca desligados: estes testes verificam o comportamento da controller,
// e a matriz de quem acessa o que tem cobertura propria em SegurancaIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CacheController")
class CacheControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CacheManager cacheManager;

    private Cache cacheMock;

    @BeforeEach
    void setUp() {
        cacheMock = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(cacheMock);
    }

    @Nested
    @DisplayName("DELETE /api/cache")
    class DeleteTodos {

        @Test
        @DisplayName("deve retornar 200 e lista com todos os 6 caches invalidados")
        void deveInvalidarTodosOsCaches() throws Exception {
            mockMvc.perform(delete("/api/cache"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.cachesInvalidados", hasSize(6)))
                    .andExpect(jsonPath("$.cachesInvalidados", containsInAnyOrder(
                        CacheConfig.CACHE_ODDS,
                        CacheConfig.CACHE_ATLETAS,
                        CacheConfig.CACHE_CLUBES,
                        CacheConfig.CACHE_PARTIDAS,
                        CacheConfig.CACHE_PONTUADOS,
                        CacheConfig.CACHE_STATUS_MERCADO
                    )))
                    .andExpect(jsonPath("$.mensagem").value("Todos os caches invalidados com sucesso."))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("deve chamar clear() para cada cache registrado")
        void deveChamarClearParaCadaCache() throws Exception {
            mockMvc.perform(delete("/api/cache"))
                    .andExpect(status().isOk());

            verify(cacheMock, times(6)).clear();
        }
    }

    @Nested
    @DisplayName("DELETE /api/cache/{nome}")
    class DeletePorNome {

        @Test
        @DisplayName("deve retornar 200 ao invalidar cache 'atletas'")
        void deveInvalidarCacheAtletas() throws Exception {
            mockMvc.perform(delete("/api/cache/atletas"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cachesInvalidados", hasSize(1)))
                    .andExpect(jsonPath("$.cachesInvalidados[0]").value("atletas"))
                    .andExpect(jsonPath("$.mensagem").value("Cache 'atletas' invalidado com sucesso."))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("deve retornar 200 ao invalidar cache 'odds'")
        void deveInvalidarCacheOdds() throws Exception {
            mockMvc.perform(delete("/api/cache/odds"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cachesInvalidados[0]").value("odds"));
        }

        @Test
        @DisplayName("deve retornar 200 ao invalidar cache 'statusMercado'")
        void deveInvalidarCacheStatusMercado() throws Exception {
            mockMvc.perform(delete("/api/cache/statusMercado"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cachesInvalidados[0]").value("statusMercado"));
        }

        @Test
        @DisplayName("deve chamar clear() exatamente uma vez ao invalidar cache especifico")
        void deveChamarClearUmaVez() throws Exception {
            mockMvc.perform(delete("/api/cache/clubes"))
                    .andExpect(status().isOk());

            verify(cacheMock, times(1)).clear();
        }

        @Test
        @DisplayName("deve retornar 400 para nome de cache invalido")
        void deveRetornar400ParaNomeInvalido() throws Exception {
            mockMvc.perform(delete("/api/cache/inexistente"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("inexistente")));
        }

        @Test
        @DisplayName("deve retornar 400 com lista de caches validos na mensagem de erro")
        void deveRetornar400ComCachesValidosNaMensagem() throws Exception {
            mockMvc.perform(delete("/api/cache/invalido"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(containsString("atletas")));
        }

        @Test
        @DisplayName("deve retornar timestamp no corpo de erro")
        void deveRetornarTimestampNoErro() throws Exception {
            mockMvc.perform(delete("/api/cache/invalido"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }
}
