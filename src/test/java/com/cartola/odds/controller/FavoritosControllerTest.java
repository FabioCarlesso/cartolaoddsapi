package com.cartola.odds.controller;

import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.response.FavoritosResponse;
import com.cartola.odds.service.ConfiguracaoService;
import com.cartola.odds.service.OddsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FavoritosController.class)
@DisplayName("FavoritosController")
class FavoritosControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OddsService          oddsService;
    @MockitoBean ConfiguracaoService  configuracaoService;

    @Nested
    @DisplayName("GET /api/favoritos — sucesso")
    class Sucesso {

        @Test
        @DisplayName("deve retornar 200 com estrutura completa")
        void deveRetornar200ComEstrutura() throws Exception {
            when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
            when(oddsService.buscarFavoritosDetalhado(3.0)).thenReturn(responseMock(3.0, 2, 1));

            mockMvc.perform(get("/api/favoritos"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.oddLimite").value(3.0))
                    .andExpect(jsonPath("$.totalJogos").isNumber())
                    .andExpect(jsonPath("$.totalFavoritos").isNumber())
                    .andExpect(jsonPath("$.totalDescartados").isNumber())
                    .andExpect(jsonPath("$.favoritos").isArray())
                    .andExpect(jsonPath("$.descartados").isArray());
        }

        @Test
        @DisplayName("deve usar oddLimite do properties quando param omitido")
        void deveUsarLimiteDoProperties() throws Exception {
            when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
            when(oddsService.buscarFavoritosDetalhado(3.0)).thenReturn(responseMock(3.0, 0, 0));

            mockMvc.perform(get("/api/favoritos"))
                    .andExpect(status().isOk());

            verify(oddsService).buscarFavoritosDetalhado(3.0);
        }

        @Test
        @DisplayName("deve usar oddLimite informado via query param")
        void deveUsarLimiteInformado() throws Exception {
            when(oddsService.buscarFavoritosDetalhado(2.5)).thenReturn(responseMock(2.5, 1, 2));

            mockMvc.perform(get("/api/favoritos").param("oddLimite", "2.5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.oddLimite").value(2.5));

            verify(oddsService).buscarFavoritosDetalhado(2.5);
        }

        @Test
        @DisplayName("deve retornar campos obrigatorios de jogo favorito")
        void deveRetornarCamposDeFavoritoCompletos() throws Exception {
            when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
            when(oddsService.buscarFavoritosDetalhado(3.0)).thenReturn(responseMock(3.0, 1, 0));

            mockMvc.perform(get("/api/favoritos"))
                    .andExpect(jsonPath("$.favoritos[0].timeFavorito").isString())
                    .andExpect(jsonPath("$.favoritos[0].oddFavorito").isNumber())
                    .andExpect(jsonPath("$.favoritos[0].timeAdversario").isString())
                    .andExpect(jsonPath("$.favoritos[0].oddAdversario").isNumber())
                    .andExpect(jsonPath("$.favoritos[0].oddEmpate").isNumber())
                    .andExpect(jsonPath("$.favoritos[0].favoritoEmCasa").isBoolean());
        }

        @Test
        @DisplayName("deve retornar campos obrigatorios de jogo descartado")
        void deveRetornarCamposDeDescartadoCompletos() throws Exception {
            when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
            when(oddsService.buscarFavoritosDetalhado(3.0)).thenReturn(responseMock(3.0, 0, 1));

            mockMvc.perform(get("/api/favoritos"))
                    .andExpect(jsonPath("$.descartados[0].timeCasa").isString())
                    .andExpect(jsonPath("$.descartados[0].oddCasa").isNumber())
                    .andExpect(jsonPath("$.descartados[0].timeVisitante").isString())
                    .andExpect(jsonPath("$.descartados[0].oddVisitante").isNumber())
                    .andExpect(jsonPath("$.descartados[0].oddEmpate").isNumber())
                    .andExpect(jsonPath("$.descartados[0].motivo").isString());
        }

        @Test
        @DisplayName("deve retornar listas vazias quando nenhum jogo disponivel")
        void deveRetornarListasVazias() throws Exception {
            when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
            when(oddsService.buscarFavoritosDetalhado(3.0))
                    .thenReturn(responseMock(3.0, 0, 0));

            mockMvc.perform(get("/api/favoritos"))
                    .andExpect(jsonPath("$.favoritos").isEmpty())
                    .andExpect(jsonPath("$.descartados").isEmpty())
                    .andExpect(jsonPath("$.totalFavoritos").value(0))
                    .andExpect(jsonPath("$.totalDescartados").value(0));
        }

        @Test
        @DisplayName("deve aceitar oddLimite exato no limite inferior (1.01)")
        void deveAceitarOddLimiteMinimo() throws Exception {
            when(oddsService.buscarFavoritosDetalhado(1.01)).thenReturn(responseMock(1.01, 0, 0));

            mockMvc.perform(get("/api/favoritos").param("oddLimite", "1.01"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("deve aceitar oddLimite alto (ex: 10.0)")
        void deveAceitarOddLimiteAlto() throws Exception {
            when(oddsService.buscarFavoritosDetalhado(10.0)).thenReturn(responseMock(10.0, 5, 0));

            mockMvc.perform(get("/api/favoritos").param("oddLimite", "10.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.oddLimite").value(10.0));
        }
    }

    @Nested
    @DisplayName("GET /api/favoritos — erros")
    class Erros {

        @Test
        @DisplayName("deve retornar 400 para oddLimite <= 1.0")
        void deveRetornar400ParaOddLimiteUm() throws Exception {
            mockMvc.perform(get("/api/favoritos").param("oddLimite", "1.0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.erro").value("Parametro invalido"))
                    .andExpect(jsonPath("$.mensagem").value(containsString("1.0")));
        }

        @Test
        @DisplayName("deve retornar 400 para oddLimite negativo")
        void deveRetornar400ParaOddLimiteNegativo() throws Exception {
            mockMvc.perform(get("/api/favoritos").param("oddLimite", "-1.5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("deve retornar 400 para oddLimite zero")
        void deveRetornar400ParaOddLimiteZero() throws Exception {
            mockMvc.perform(get("/api/favoritos").param("oddLimite", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 502 quando Odds API falhar")
        void deveRetornar502QuandoApiExternaFalhar() throws Exception {
            when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
            when(oddsService.buscarFavoritosDetalhado(anyDouble()))
                    .thenThrow(new org.springframework.web.client.RestClientException("Timeout"));

            mockMvc.perform(get("/api/favoritos"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.status").value(502));
        }

        @Test
        @DisplayName("deve incluir timestamp no corpo de erro")
        void deveIncluirTimestampNoErro() throws Exception {
            mockMvc.perform(get("/api/favoritos").param("oddLimite", "0.5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // ── Helper ───────────────────────────────────────────────────────

    private FavoritosResponse responseMock(double limite, int qtdFav, int qtdDesc) {
        var favoritos = new java.util.ArrayList<FavoritosResponse.JogoFavoritoDto>();
        for (int i = 0; i < qtdFav; i++) {
            favoritos.add(FavoritosResponse.JogoFavoritoDto.builder()
                    .timeFavorito("Flamengo " + i)
                    .oddFavorito(1.90 + i * 0.1)
                    .timeAdversario("Vasco " + i)
                    .oddAdversario(4.00)
                    .oddEmpate(3.30)
                    .favoritoEmCasa(true)
                    .build());
        }

        var descartados = new java.util.ArrayList<FavoritosResponse.JogoDescartadoDto>();
        for (int i = 0; i < qtdDesc; i++) {
            descartados.add(FavoritosResponse.JogoDescartadoDto.builder()
                    .timeCasa("Fortaleza " + i)
                    .oddCasa(3.20)
                    .timeVisitante("Bahia " + i)
                    .oddVisitante(3.40)
                    .oddEmpate(3.10)
                    .motivo("Menor odd (3.20) acima do limite (" + limite + ")")
                    .build());
        }

        return FavoritosResponse.builder()
                .oddLimite(limite)
                .totalJogos(qtdFav + qtdDesc)
                .totalFavoritos(qtdFav)
                .totalDescartados(qtdDesc)
                .favoritos(favoritos)
                .descartados(descartados)
                .build();
    }
}
