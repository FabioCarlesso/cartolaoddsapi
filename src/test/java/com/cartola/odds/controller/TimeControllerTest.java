package com.cartola.odds.controller;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.service.PipelineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TimeController.class)
@DisplayName("TimeController")
class TimeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PipelineService pipelineService;

    @Nested
    @DisplayName("GET /api/time")
    class GetApiTime {

        @Test
        @DisplayName("deve retornar 200 com corpo preenchido quando pipeline executar com sucesso")
        void deveRetornar200ComTime() throws Exception {
            when(pipelineService.executar()).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.rodada").value(15))
                    .andExpect(jsonPath("$.custoTotal").isNumber())
                    .andExpect(jsonPath("$.titulares").isMap())
                    .andExpect(jsonPath("$.reservas").isMap())
                    .andExpect(jsonPath("$.alertasDuvida").isArray());
        }

        @Test
        @DisplayName("deve retornar 422 quando pool de atletas estiver vazio")
        void deveRetornar422QuandoPoolVazio() throws Exception {
            when(pipelineService.executar())
                    .thenThrow(new IllegalStateException("Nenhum atleta disponivel"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.erro").value("Erro no pipeline"))
                    .andExpect(jsonPath("$.mensagem").value(containsString("Nenhum atleta")));
        }

        @Test
        @DisplayName("deve retornar 502 quando API externa falhar")
        void deveRetornar502QuandoApiExternaFalhar() throws Exception {
            when(pipelineService.executar())
                    .thenThrow(new org.springframework.web.client.RestClientException("Timeout"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.status").value(502));
        }

        @Test
        @DisplayName("deve retornar 500 para erros inesperados")
        void deveRetornar500ParaErroGenerico() throws Exception {
            when(pipelineService.executar())
                    .thenThrow(new RuntimeException("Erro inesperado"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500));
        }


        @Test
        @DisplayName("deve retornar avisoMercado preenchido quando mercado fechado")
        void deveRetornarAvisoQuandoMercadoFechado() throws Exception {
            var timeComAviso = criarTimeMockComAviso("Mercado fechado. Rodada em andamento.");
            when(pipelineService.executar()).thenReturn(timeComAviso);

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avisoMercado").value("Mercado fechado. Rodada em andamento."));
        }

        @Test
        @DisplayName("deve retornar avisoMercado null quando mercado aberto")
        void deveRetornarAvisoNullQuandoMercadoAberto() throws Exception {
            when(pipelineService.executar()).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avisoMercado").doesNotExist());
        }

        @Test
        @DisplayName("deve retornar capitao preenchido quando existe")
        void deveRetornarCapitao() throws Exception {
            when(pipelineService.executar()).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.capitao").isNotEmpty())
                    .andExpect(jsonPath("$.capitao.apelido").isString())
                    .andExpect(jsonPath("$.capitao.siglaClube").isString())
                    .andExpect(jsonPath("$.capitao.score").isNumber());
        }

        @Test
        @DisplayName("deve retornar todos os campos obrigatorios do atleta")
        void deveRetornarEstruturaCompletaDoAtleta() throws Exception {
            when(pipelineService.executar()).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.capitao.apelido").exists())
                    .andExpect(jsonPath("$.capitao.siglaClube").exists())
                    .andExpect(jsonPath("$.capitao.nomeClube").exists())
                    .andExpect(jsonPath("$.capitao.posicao").exists())
                    .andExpect(jsonPath("$.capitao.status").exists())
                    .andExpect(jsonPath("$.capitao.mediaPontos").exists())
                    .andExpect(jsonPath("$.capitao.preco").exists())
                    .andExpect(jsonPath("$.capitao.score").exists());
        }

        @Test
        @DisplayName("deve incluir timestamp no corpo de resposta de erro")
        void deveRetornarTimestampNoErro() throws Exception {
            when(pipelineService.executar())
                    .thenThrow(new IllegalStateException("Pool vazio"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private Time criarTimeMockComAviso(String aviso) {
        var capitao = Atleta.builder()
                .atletaId(1).apelido("Hulk").posicao(Posicao.ATA)
                .clubeId(1).nomeClube("ATM").siglaClube("ATM").nomeClubeNorm("atm")
                .status(StatusAtleta.PROVAVEL).mediaPontos(9.5).valorizacao(3.2)
                .preco(22.0).desempenhoRecente(0.0).score(8.54).build();
        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        titulares.put(Posicao.ATA, List.of(capitao));
        return Time.builder().rodada(15).avisoMercado(aviso)
                .titulares(titulares).reservas(Map.of()).capitao(capitao)
                .reservaLuxo(null).alertasDuvida(List.of()).custoTotal(142.5).build();
    }

    private Time criarTimeMock() {
        var capitao = Atleta.builder()
                .apelido("Hulk")
                .posicao(Posicao.ATA)
                .clubeId(1)
                .nomeClube("Atletico Mineiro")
                .siglaClube("ATM")
                .nomeClubeNorm("atletico mineiro")
                .status(StatusAtleta.PROVAVEL)
                .mediaPontos(9.5)
                .valorizacao(3.2)
                .preco(22.0)
                .score(8.54)
                .build();

        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        titulares.put(Posicao.ATA, List.of(capitao));

        return Time.builder()
                .rodada(15)
                .avisoMercado(null)
                .titulares(titulares)
                .reservas(Map.of())
                .capitao(capitao)
                .reservaLuxo(null)
                .alertasDuvida(List.of())
                .custoTotal(142.5)
                .build();
    }
}
