package com.cartola.odds.controller;

import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.response.ConfiguracaoResponse;
import com.cartola.odds.service.ConfiguracaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfiguracaoController.class)
// Filtros de seguranca desligados: estes testes verificam o comportamento da controller,
// e a matriz de quem acessa o que tem cobertura propria em SegurancaIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ConfiguracaoController")
class ConfiguracaoControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ConfiguracaoService configuracaoService;

    private ConfiguracaoResponse responsePadrao;

    @BeforeEach
    void setUp() {
        responsePadrao = ConfiguracaoResponse.from(Configuracao.defaults());
        when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
    }

    @Nested
    @DisplayName("GET /api/config")
    class GetConfig {

        @Test
        @DisplayName("deve retornar 200 com todos os campos preenchidos")
        void deveRetornar200ComCampos() throws Exception {
            mockMvc.perform(get("/api/config"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.oddLimite").value(3.0))
                    .andExpect(jsonPath("$.pesoMediaPontos").value(0.40))
                    .andExpect(jsonPath("$.pesoValorizacao").value(0.20))
                    .andExpect(jsonPath("$.pesoDesempenho").value(0.20))
                    .andExpect(jsonPath("$.pesoFatorCasa").value(0.10))
                    .andExpect(jsonPath("$.pesoTimeFavorito").value(0.10))
                    .andExpect(jsonPath("$.pesoDesvio").value(0.05))
                    .andExpect(jsonPath("$.formacaoGol").value(1))
                    .andExpect(jsonPath("$.formacaoLat").value(2))
                    .andExpect(jsonPath("$.formacaoZag").value(2))
                    .andExpect(jsonPath("$.formacaoMei").value(3))
                    .andExpect(jsonPath("$.formacaoAta").value(3))
                    .andExpect(jsonPath("$.formacaoTec").value(1))
                    .andExpect(jsonPath("$.evitarMesmoClubeDefesa").value(true))
                    .andExpect(jsonPath("$.limiteAtletasPorClube").value(4))
                    .andExpect(jsonPath("$.budgetMaximo").value(0.0));
        }

        @Test
        @DisplayName("deve retornar timestamp preenchido")
        void deveRetornarTimestamp() throws Exception {
            mockMvc.perform(get("/api/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.updatedAt").exists());
        }
    }

    @Nested
    @DisplayName("PATCH /api/config")
    class PatchConfig {

        @Test
        @DisplayName("deve retornar 200 ao atualizar oddLimite")
        void deveAtualizarOddLimite() throws Exception {
            when(configuracaoService.atualizar(any())).thenReturn(responsePadrao);

            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oddLimite\": 2.5}"))
                    .andExpect(status().isOk());

            verify(configuracaoService).atualizar(any());
        }

        @Test
        @DisplayName("deve retornar 200 ao atualizar pesos")
        void deveAtualizarPesos() throws Exception {
            when(configuracaoService.atualizar(any())).thenReturn(responsePadrao);

            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                  "pesoMediaPontos": 0.35,
                                  "pesoValorizacao": 0.25,
                                  "pesoDesempenho":  0.20,
                                  "pesoFatorCasa":   0.10,
                                  "pesoTimeFavorito":0.10
                                }
                                """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("deve retornar 200 ao atualizar regra de defesa")
        void deveAtualizarRegraDefesa() throws Exception {
            when(configuracaoService.atualizar(any())).thenReturn(responsePadrao);

            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"evitarMesmoClubeDefesa\": false}"))
                    .andExpect(status().isOk());

            verify(configuracaoService).atualizar(any());
        }

        @Test
        @DisplayName("deve retornar 200 ao atualizar limite de atletas por clube")
        void deveAtualizarLimiteAtletasPorClube() throws Exception {
            when(configuracaoService.atualizar(any())).thenReturn(responsePadrao);

            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"limiteAtletasPorClube\": 5}"))
                    .andExpect(status().isOk());

            verify(configuracaoService).atualizar(any());
        }

        @Test
        @DisplayName("deve retornar 400 para oddLimite <= 1.0")
        void deveRetornar400ParaOddLimiteInvalido() throws Exception {
            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"oddLimite\": 0.5}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 para peso negativo")
        void deveRetornar400ParaPesoNegativo() throws Exception {
            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoMediaPontos\": -0.10}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 para peso maior que 1.0")
        void deveRetornar400ParaPesoMaiorQueUm() throws Exception {
            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoMediaPontos\": 1.5}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 200 ao atualizar budget maximo")
        void deveAtualizarBudgetMaximo() throws Exception {
            when(configuracaoService.atualizar(any())).thenReturn(responsePadrao);

            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"budgetMaximo\": 120.0}"))
                    .andExpect(status().isOk());

            verify(configuracaoService).atualizar(any());
        }

        @Test
        @DisplayName("deve retornar 400 para budgetMaximo negativo")
        void deveRetornar400ParaBudgetMaximoNegativo() throws Exception {
            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"budgetMaximo\": -10.0}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 200 ao atualizar pesoDesvio")
        void deveAtualizarPesoDesvio() throws Exception {
            when(configuracaoService.atualizar(any())).thenReturn(responsePadrao);

            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoDesvio\": 0.10}"))
                    .andExpect(status().isOk());

            verify(configuracaoService).atualizar(any());
        }

        @Test
        @DisplayName("deve retornar 400 para pesoDesvio > 1.0")
        void deveRetornar400ParaPesoDesvioMaiorQueUm() throws Exception {
            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoDesvio\": 1.5}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 para pesoDesvio negativo")
        void deveRetornar400ParaPesoDesvioNegativo() throws Exception {
            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoDesvio\": -0.10}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 para limiteAtletasPorClube < 1")
        void deveRetornar400ParaLimiteAtletasPorClubeInvalido() throws Exception {
            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"limiteAtletasPorClube\": 0}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando soma dos pesos != 1.0")
        void deveRetornar400QuandoSomaPesosInvalida() throws Exception {
            when(configuracaoService.atualizar(any()))
                    .thenThrow(new IllegalArgumentException("A soma dos pesos deve ser 1.0. Soma atual: 0.900"));

            mockMvc.perform(patch("/api/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pesoMediaPontos\": 0.10}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("soma dos pesos")));
        }
    }

    @Nested
    @DisplayName("POST /api/config/reset")
    class PostReset {

        @Test
        @DisplayName("deve retornar 200 com valores padrao apos reset")
        void deveResetarParaDefaults() throws Exception {
            when(configuracaoService.resetar()).thenReturn(responsePadrao);

            mockMvc.perform(post("/api/config/reset"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.oddLimite").value(3.0))
                    .andExpect(jsonPath("$.pesoMediaPontos").value(0.40));

            verify(configuracaoService).resetar();
        }
    }
}
