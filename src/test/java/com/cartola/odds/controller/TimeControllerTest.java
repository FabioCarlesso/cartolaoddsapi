package com.cartola.odds.controller;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.service.EscalacaoService;
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

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TimeController.class)
@DisplayName("TimeController")
class TimeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PipelineService pipelineService;
    @MockitoBean EscalacaoService escalacaoService;

    @Nested
    @DisplayName("GET /api/time")
    class GetApiTime {

        @Test
        @DisplayName("deve retornar 200 com corpo preenchido quando pipeline executar com sucesso")
        void deveRetornar200ComTime() throws Exception {
            when(pipelineService.executar(any())).thenReturn(criarTimeMock());

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
            when(pipelineService.executar(any()))
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
            when(pipelineService.executar(any()))
                    .thenThrow(new org.springframework.web.client.RestClientException("Timeout"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.status").value(502));
        }

        @Test
        @DisplayName("deve retornar 500 para erros inesperados")
        void deveRetornar500ParaErroGenerico() throws Exception {
            when(pipelineService.executar(any()))
                    .thenThrow(new RuntimeException("Erro inesperado"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500));
        }


        @Test
        @DisplayName("deve retornar avisoMercado preenchido quando mercado fechado")
        void deveRetornarAvisoQuandoMercadoFechado() throws Exception {
            var timeComAviso = criarTimeMockComAviso("Mercado fechado. Rodada em andamento.");
            when(pipelineService.executar(any())).thenReturn(timeComAviso);

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avisoMercado").value("Mercado fechado. Rodada em andamento."));
        }

        @Test
        @DisplayName("deve retornar avisoMercado null quando mercado aberto")
        void deveRetornarAvisoNullQuandoMercadoAberto() throws Exception {
            when(pipelineService.executar(any())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avisoMercado").doesNotExist());
        }

        @Test
        @DisplayName("deve retornar capitao preenchido quando existe")
        void deveRetornarCapitao() throws Exception {
            when(pipelineService.executar(any())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.capitao").isNotEmpty())
                    .andExpect(jsonPath("$.capitao.apelido").isString())
                    .andExpect(jsonPath("$.capitao.siglaClube").isString())
                    .andExpect(jsonPath("$.capitao.score").isNumber());
        }

        @Test
        @DisplayName("deve retornar todos os campos obrigatorios do atleta")
        void deveRetornarEstruturaCompletaDoAtleta() throws Exception {
            when(pipelineService.executar(any())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.capitao.apelido").exists())
                    .andExpect(jsonPath("$.capitao.siglaClube").exists())
                    .andExpect(jsonPath("$.capitao.nomeClube").exists())
                    .andExpect(jsonPath("$.capitao.posicao").exists())
                    .andExpect(jsonPath("$.capitao.status").exists())
                    .andExpect(jsonPath("$.capitao.mediaPontos").exists())
                    .andExpect(jsonPath("$.capitao.preco").exists())
                    .andExpect(jsonPath("$.capitao.score").exists())
                    .andExpect(jsonPath("$.capitao.desvioPadrao").value(1.25))
                    .andExpect(jsonPath("$.capitao.rodadasConsideradas").value(5));
        }

        @Test
        @DisplayName("deve persistir a escalacao apos montar o time")
        void devePersistirEscalacao() throws Exception {
            when(pipelineService.executar(any())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk());

            verify(escalacaoService).salvarEscalacao(any(), eq(15));
        }

        @Test
        @DisplayName("deve retornar 200 mesmo se a persistencia da escalacao falhar (nao bloqueante)")
        void deveRetornar200QuandoPersistenciaFalha() throws Exception {
            when(pipelineService.executar(any())).thenReturn(criarTimeMock());
            doThrow(new RuntimeException("falha ao salvar"))
                    .when(escalacaoService).salvarEscalacao(any(), any());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rodada").value(15));
        }

        @Test
        @DisplayName("deve incluir timestamp no corpo de resposta de erro")
        void deveRetornarTimestampNoErro() throws Exception {
            when(pipelineService.executar(any()))
                    .thenThrow(new IllegalStateException("Pool vazio"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("deve usar estrategia SCORE_MAXIMO quando orcamento nao informado")
        void deveEstrategiaScoreMaximoSemOrcamento() throws Exception {
            when(pipelineService.executar(any())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estrategia").value("SCORE_MAXIMO"))
                    .andExpect(jsonPath("$.orcamentoInformado").doesNotExist())
                    .andExpect(jsonPath("$.saldoRestante").doesNotExist());

            verify(pipelineService).executar(isNull());
        }

        @Test
        @DisplayName("deve propagar orcamento e expor campos de custo-beneficio quando informado")
        void devePropagarOrcamentoEExporCampos() throws Exception {
            when(pipelineService.executar(eq(120.0)))
                    .thenReturn(criarTimeMockComOrcamento(120.0, 118.3));

            mockMvc.perform(get("/api/time").param("orcamento", "120.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estrategia").value("CUSTO_BENEFICIO"))
                    .andExpect(jsonPath("$.orcamentoInformado").value(120.0))
                    .andExpect(jsonPath("$.custoTotal").value(118.3))
                    .andExpect(jsonPath("$.saldoRestante").value(closeTo(1.7, 0.0001)));

            verify(pipelineService).executar(eq(120.0));
        }

        @Test
        @DisplayName("deve retornar 400 quando orcamento for menor ou igual a zero")
        void deveRetornar400QuandoOrcamentoInvalido() throws Exception {
            mockMvc.perform(get("/api/time").param("orcamento", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("orcamento")));

            verify(pipelineService, never()).executar(any());
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

    private Time criarTimeMockComOrcamento(double orcamento, double custoTotal) {
        var capitao = Atleta.builder()
                .atletaId(1).apelido("Hulk").posicao(Posicao.ATA)
                .clubeId(1).nomeClube("ATM").siglaClube("ATM").nomeClubeNorm("atm")
                .status(StatusAtleta.PROVAVEL).mediaPontos(9.5).valorizacao(3.2)
                .preco(22.0).desempenhoRecente(0.0).score(8.54).build();
        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        titulares.put(Posicao.ATA, List.of(capitao));
        return Time.builder().rodada(15).avisoMercado(null)
                .titulares(titulares).reservas(Map.of()).capitao(capitao)
                .reservaLuxo(null).alertasDuvida(List.of()).custoTotal(custoTotal)
                .orcamentoInformado(orcamento)
                .saldoRestante(orcamento - custoTotal)
                .estrategia(com.cartola.odds.model.enums.Estrategia.CUSTO_BENEFICIO)
                .build();
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
                .desvioPadrao(1.25)
                .rodadasConsideradas(5)
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
                .estrategia(com.cartola.odds.model.enums.Estrategia.SCORE_MAXIMO)
                .build();
    }
}
