package com.cartola.odds.controller;

import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.model.response.RankingResponse;
import com.cartola.odds.model.response.RankingResponse.AtletaRankingDto;
import com.cartola.odds.service.RankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RankingController.class)
// Filtros de seguranca desligados: estes testes verificam o comportamento da controller,
// e a matriz de quem acessa o que tem cobertura propria em SegurancaIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RankingController")
class RankingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RankingService rankingService;

    @Nested
    @DisplayName("GET /api/ranking — sucesso")
    class Sucesso {

        @Test
        @DisplayName("deve retornar 200 com estrutura completa")
        void deveRetornar200ComEstrutura() throws Exception {
            when(rankingService.buscarRanking(null, 25, false)).thenReturn(rankingMock("TODAS", 25, 3));

            mockMvc.perform(get("/api/ranking"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.rodada").value(15))
                    .andExpect(jsonPath("$.posicao").value("TODAS"))
                    .andExpect(jsonPath("$.limite").value(25))
                    .andExpect(jsonPath("$.totalDisponivel").isNumber())
                    .andExpect(jsonPath("$.atletas").isArray())
                    .andExpect(jsonPath("$.atletas", hasSize(3)));
        }

        @Test
        @DisplayName("deve chamar service sem posicao quando parametro omitido")
        void deveChamarSemPosicaoQuandoOmitido() throws Exception {
            when(rankingService.buscarRanking(null, 25, false)).thenReturn(rankingMock("TODAS", 25, 0));

            mockMvc.perform(get("/api/ranking"))
                    .andExpect(status().isOk());

            verify(rankingService).buscarRanking(null, 25, false);
        }

        @Test
        @DisplayName("deve passar posicao ATA quando informada via query param")
        void devePassarPosicaoAta() throws Exception {
            when(rankingService.buscarRanking(eq(Posicao.ATA), eq(25), eq(false)))
                    .thenReturn(rankingMock("ATA", 25, 2));

            mockMvc.perform(get("/api/ranking").param("posicao", "ATA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posicao").value("ATA"));

            verify(rankingService).buscarRanking(Posicao.ATA, 25, false);
        }

        @Test
        @DisplayName("deve passar limite customizado ao service")
        void devePassarLimiteCustomizado() throws Exception {
            when(rankingService.buscarRanking(null, 10, false)).thenReturn(rankingMock("TODAS", 10, 10));

            mockMvc.perform(get("/api/ranking").param("limite", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.limite").value(10));

            verify(rankingService).buscarRanking(null, 10, false);
        }

        @Test
        @DisplayName("deve usar excluirDuvida=false como padrao quando omitido")
        void deveUsarExcluirDuvidaFalsePorPadrao() throws Exception {
            when(rankingService.buscarRanking(null, 25, false)).thenReturn(rankingMock("TODAS", 25, 3));

            mockMvc.perform(get("/api/ranking"))
                    .andExpect(status().isOk());

            verify(rankingService).buscarRanking(null, 25, false);
        }

        @Test
        @DisplayName("deve propagar excluirDuvida=true ao service")
        void devePropagarExcluirDuvidaTrue() throws Exception {
            when(rankingService.buscarRanking(eq(Posicao.MEI), eq(5), eq(true)))
                    .thenReturn(rankingMock("MEI", 5, 2));

            mockMvc.perform(get("/api/ranking")
                            .param("posicao", "MEI")
                            .param("limite", "5")
                            .param("excluirDuvida", "true"))
                    .andExpect(status().isOk());

            verify(rankingService).buscarRanking(Posicao.MEI, 5, true);
        }

        @Test
        @DisplayName("deve aceitar posicao em minusculas (case insensitive)")
        void deveAceitarPosicaoMinuscula() throws Exception {
            when(rankingService.buscarRanking(eq(Posicao.MEI), eq(25), eq(false)))
                    .thenReturn(rankingMock("MEI", 25, 5));

            mockMvc.perform(get("/api/ranking").param("posicao", "mei"))
                    .andExpect(status().isOk());

            verify(rankingService).buscarRanking(Posicao.MEI, 25, false);
        }

        @Test
        @DisplayName("deve retornar campos obrigatorios de cada atleta no ranking")
        void deveRetornarCamposObrigatoriosDoAtleta() throws Exception {
            when(rankingService.buscarRanking(null, 25, false)).thenReturn(rankingMock("TODAS", 25, 1));

            mockMvc.perform(get("/api/ranking"))
                    .andExpect(jsonPath("$.atletas[0].rank").value(1))
                    .andExpect(jsonPath("$.atletas[0].apelido").isString())
                    .andExpect(jsonPath("$.atletas[0].formatado").isString())
                    .andExpect(jsonPath("$.atletas[0].siglaClube").isString())
                    .andExpect(jsonPath("$.atletas[0].posicao").isString())
                    .andExpect(jsonPath("$.atletas[0].status").isString())
                    .andExpect(jsonPath("$.atletas[0].score").isNumber())
                    .andExpect(jsonPath("$.atletas[0].preco").isNumber())
                    .andExpect(jsonPath("$.atletas[0].mediaPontos").isNumber())
                    .andExpect(jsonPath("$.atletas[0].desvioPadrao").value(1.25))
                    .andExpect(jsonPath("$.atletas[0].rodadasConsideradas").value(5))
                    .andExpect(jsonPath("$.atletas[0].emDuvida").isBoolean());
        }

        @Test
        @DisplayName("deve retornar lista vazia quando nenhum atleta disponivel na posicao")
        void deveRetornarListaVaziaQuandoNenhumAtleta() throws Exception {
            when(rankingService.buscarRanking(Posicao.TEC, 25, false))
                    .thenReturn(rankingMock("TEC", 25, 0));

            mockMvc.perform(get("/api/ranking").param("posicao", "TEC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.atletas").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/ranking — erros")
    class Erros {

        @Test
        @DisplayName("deve retornar 400 para posicao invalida")
        void deveRetornar400ParaPosicaoInvalida() throws Exception {
            mockMvc.perform(get("/api/ranking").param("posicao", "INVALIDA"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.erro").value("Parametro invalido"))
                    .andExpect(jsonPath("$.mensagem").value(containsString("INVALIDA")));
        }

        @Test
        @DisplayName("deve retornar 400 para posicao DEF (nao existe no Cartola)")
        void deveRetornar400ParaDefNaoExistente() throws Exception {
            mockMvc.perform(get("/api/ranking").param("posicao", "DEF"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("deve retornar 422 quando pipeline nao encontra atletas")
        void deveRetornar422QuandoPipelineVazio() throws Exception {
            when(rankingService.buscarRanking(any(), anyInt(), anyBoolean()))
                    .thenThrow(new IllegalStateException("Nenhum atleta disponivel"));

            mockMvc.perform(get("/api/ranking"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422));
        }

        @Test
        @DisplayName("deve retornar 502 quando API externa falhar")
        void deveRetornar502QuandoApiExternaFalhar() throws Exception {
            when(rankingService.buscarRanking(any(), anyInt(), anyBoolean()))
                    .thenThrow(new org.springframework.web.client.RestClientException("Timeout"));

            mockMvc.perform(get("/api/ranking"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.status").value(502));
        }

        @Test
        @DisplayName("deve incluir timestamp na resposta de erro")
        void deveIncluirTimestampNoErro() throws Exception {
            mockMvc.perform(get("/api/ranking").param("posicao", "XPTO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    // ── Helper ───────────────────────────────────────────────────────

    private RankingResponse rankingMock(String posicao, int limite, int qtdAtletas) {
        var atletas = new java.util.ArrayList<AtletaRankingDto>();
        for (int i = 1; i <= qtdAtletas; i++) {
            atletas.add(AtletaRankingDto.builder()
                    .rank(i)
                    .apelido("Jogador " + i)
                    .formatado("Jogador " + i + " (FLA)")
                    .siglaClube("FLA")
                    .nomeClube("Flamengo")
                    .posicao(posicao.equals("TODAS") ? "ATA" : posicao)
                    .status(StatusAtleta.PROVAVEL.getLabel())
                    .mediaPontos(10.0 - i)
                    .valorizacao(1.0)
                    .preco(20.0 - i)
                    .score(10.0 - i)
                    .desvioPadrao(1.25)
                    .rodadasConsideradas(5)
                    .emDuvida(false)
                    .build());
        }
        return RankingResponse.builder()
                .rodada(15)
                .posicao(posicao)
                .limite(limite)
                .totalDisponivel(qtdAtletas)
                .atletas(atletas)
                .build();
    }
}
