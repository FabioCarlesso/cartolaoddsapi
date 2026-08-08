package com.cartola.odds.controller;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.FormacaoConfig;
import com.cartola.odds.model.ResultadoFormacao;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.service.EscalacaoService;
import com.cartola.odds.service.PipelineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
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
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(criarTimeMock());

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
            when(pipelineService.executar(any(), anyBoolean()))
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
            when(pipelineService.executar(any(), anyBoolean()))
                    .thenThrow(new org.springframework.web.client.RestClientException("Timeout"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.status").value(502));
        }

        @Test
        @DisplayName("deve retornar 500 para erros inesperados")
        void deveRetornar500ParaErroGenerico() throws Exception {
            when(pipelineService.executar(any(), anyBoolean()))
                    .thenThrow(new RuntimeException("Erro inesperado"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500));
        }


        @Test
        @DisplayName("deve retornar avisoMercado preenchido quando mercado fechado")
        void deveRetornarAvisoQuandoMercadoFechado() throws Exception {
            var timeComAviso = criarTimeMockComAviso("Mercado fechado. Rodada em andamento.");
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(timeComAviso);

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avisoMercado").value("Mercado fechado. Rodada em andamento."));
        }

        @Test
        @DisplayName("deve retornar avisoMercado null quando mercado aberto")
        void deveRetornarAvisoNullQuandoMercadoAberto() throws Exception {
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avisoMercado").doesNotExist());
        }

        @Test
        @DisplayName("deve retornar capitao preenchido quando existe")
        void deveRetornarCapitao() throws Exception {
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.capitao").isNotEmpty())
                    .andExpect(jsonPath("$.capitao.apelido").isString())
                    .andExpect(jsonPath("$.capitao.siglaClube").isString())
                    .andExpect(jsonPath("$.capitao.score").isNumber());
        }

        @Test
        @DisplayName("deve retornar todos os campos obrigatorios do atleta")
        void deveRetornarEstruturaCompletaDoAtleta() throws Exception {
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(criarTimeMock());

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
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk());

            verify(escalacaoService).salvarEscalacao(any(), eq(15));
        }

        @Test
        @DisplayName("deve retornar 200 mesmo se a persistencia da escalacao falhar (nao bloqueante)")
        void deveRetornar200QuandoPersistenciaFalha() throws Exception {
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(criarTimeMock());
            doThrow(new RuntimeException("falha ao salvar"))
                    .when(escalacaoService).salvarEscalacao(any(), any());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rodada").value(15));
        }

        @Test
        @DisplayName("deve incluir timestamp no corpo de resposta de erro")
        void deveRetornarTimestampNoErro() throws Exception {
            when(pipelineService.executar(any(), anyBoolean()))
                    .thenThrow(new IllegalStateException("Pool vazio"));

            mockMvc.perform(get("/api/time"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("deve usar estrategia SCORE_MAXIMO quando orcamento nao informado")
        void deveEstrategiaScoreMaximoSemOrcamento() throws Exception {
            when(pipelineService.executar(any(), anyBoolean())).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estrategia").value("SCORE_MAXIMO"))
                    .andExpect(jsonPath("$.formacaoCompleta").value(true))
                    .andExpect(jsonPath("$.orcamentoInformado").doesNotExist())
                    .andExpect(jsonPath("$.saldoRestante").doesNotExist())
                    .andExpect(jsonPath("$.avisoOrcamento").doesNotExist());

            verify(pipelineService).executar(isNull(), eq(false));
        }

        @Test
        @DisplayName("deve propagar orcamento e expor campos de orcamento quando informado")
        void devePropagarOrcamentoEExporCampos() throws Exception {
            when(pipelineService.executar(eq(120.0), anyBoolean()))
                    .thenReturn(criarTimeMockComOrcamento(120.0, 118.3));

            mockMvc.perform(get("/api/time").param("orcamento", "120.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estrategia").value("SCORE_MAXIMO"))
                    .andExpect(jsonPath("$.orcamentoInformado").value(120.0))
                    .andExpect(jsonPath("$.custoTotal").value(118.3))
                    .andExpect(jsonPath("$.saldoRestante").value(1.7))
                    .andExpect(jsonPath("$.formacaoCompleta").value(true))
                    .andExpect(jsonPath("$.avisoOrcamento").doesNotExist());

            verify(pipelineService).executar(eq(120.0), eq(false));
        }

        @Test
        @DisplayName("deve expor avisoOrcamento e formacaoCompleta=false quando orcamento insuficiente")
        void deveExporAvisoQuandoOrcamentoInsuficiente() throws Exception {
            when(pipelineService.executar(eq(30.0), anyBoolean()))
                    .thenReturn(criarTimeMockIncompleto(30.0, 24.0));

            mockMvc.perform(get("/api/time").param("orcamento", "30.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.formacaoCompleta").value(false))
                    .andExpect(jsonPath("$.saldoRestante").value(6.0))
                    .andExpect(jsonPath("$.avisoOrcamento").value(containsString("insuficiente")));
        }

        @Test
        @DisplayName("deve retornar 400 quando orcamento for menor ou igual a zero")
        void deveRetornar400QuandoOrcamentoInvalido() throws Exception {
            mockMvc.perform(get("/api/time").param("orcamento", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("orcamento")));

            verify(pipelineService, never()).executar(any(), anyBoolean());
        }

        @Test
        @DisplayName("deve propagar excluirDuvida=true ao pipeline")
        void devePropagarExcluirDuvida() throws Exception {
            when(pipelineService.executar(isNull(), eq(true))).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time").param("excluirDuvida", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rodada").value(15));

            verify(pipelineService).executar(isNull(), eq(true));
        }

        @Test
        @DisplayName("deve combinar excluirDuvida com orcamento")
        void deveCombinarExcluirDuvidaComOrcamento() throws Exception {
            when(pipelineService.executar(eq(120.0), eq(true)))
                    .thenReturn(criarTimeMockComOrcamento(120.0, 118.3));

            mockMvc.perform(get("/api/time")
                            .param("orcamento", "120.0")
                            .param("excluirDuvida", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orcamentoInformado").value(120.0));

            verify(pipelineService).executar(eq(120.0), eq(true));
        }

        @Test
        @DisplayName("nao deve persistir escalacao quando excluirDuvida=true (consulta comparativa)")
        void naoDevePersistirEscalacaoComExcluirDuvida() throws Exception {
            when(pipelineService.executar(isNull(), eq(true))).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time").param("excluirDuvida", "true"))
                    .andExpect(status().isOk());

            verify(escalacaoService, never()).salvarEscalacao(any(), any());
        }

        @Test
        @DisplayName("deve persistir escalacao quando orcamento e informado (time real da rodada)")
        void devePersistirEscalacaoComOrcamento() throws Exception {
            when(pipelineService.executar(eq(120.0), eq(false)))
                    .thenReturn(criarTimeMockComOrcamento(120.0, 118.3));

            mockMvc.perform(get("/api/time").param("orcamento", "120.0"))
                    .andExpect(status().isOk());

            verify(escalacaoService).salvarEscalacao(any(), eq(15));
        }

        @Test
        @DisplayName("nao deve persistir escalacao ao combinar orcamento com excluirDuvida=true")
        void naoDevePersistirEscalacaoComOrcamentoEExcluirDuvida() throws Exception {
            when(pipelineService.executar(eq(120.0), eq(true)))
                    .thenReturn(criarTimeMockComOrcamento(120.0, 118.3));

            mockMvc.perform(get("/api/time")
                            .param("orcamento", "120.0")
                            .param("excluirDuvida", "true"))
                    .andExpect(status().isOk());

            verify(escalacaoService, never()).salvarEscalacao(any(), any());
        }

        @Test
        @DisplayName("deve persistir escalacao apenas na montagem padrao com excluirDuvida=false explicito")
        void devePersistirEscalacaoComExcluirDuvidaFalseExplicito() throws Exception {
            when(pipelineService.executar(isNull(), eq(false))).thenReturn(criarTimeMock());

            mockMvc.perform(get("/api/time").param("excluirDuvida", "false"))
                    .andExpect(status().isOk());

            verify(escalacaoService).salvarEscalacao(any(), eq(15));
        }

        @Test
        @DisplayName("deve retornar 400 quando excluirDuvida tem valor nao booleano")
        void deveRetornar400QuandoExcluirDuvidaInvalido() throws Exception {
            mockMvc.perform(get("/api/time").param("excluirDuvida", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.erro").value("Parametro invalido"))
                    .andExpect(jsonPath("$.mensagem").value(containsString("excluirDuvida")))
                    .andExpect(jsonPath("$.mensagem").value(containsString("abc")));

            verify(pipelineService, never()).executar(any(), anyBoolean());
        }

        @Test
        @DisplayName("deve retornar 400 quando orcamento tem valor nao numerico")
        void deveRetornar400QuandoOrcamentoNaoNumerico() throws Exception {
            mockMvc.perform(get("/api/time").param("orcamento", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("orcamento")))
                    .andExpect(jsonPath("$.mensagem").value(containsString("Double")));

            verify(pipelineService, never()).executar(any(), anyBoolean());
        }

        @Test
        @DisplayName("deve truncar valor muito longo na mensagem de erro de tipo invalido")
        void deveTruncarValorLongoNaMensagemDeErro() throws Exception {
            var valorLongo = "x".repeat(500);

            var corpo = mockMvc.perform(get("/api/time").param("excluirDuvida", valorLongo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(containsString("...")))
                    .andReturn().getResponse().getContentAsString();

            assertThat(corpo).doesNotContain(valorLongo);
            assertThat(corpo.length()).isLessThan(300);
        }
    }

    @Nested
    @DisplayName("GET /api/time/comparar")
    class CompararFormacoes {

        @Test
        @DisplayName("deve retornar 200 com resultados ordenados por scoreTotal e melhorFormacao")
        void deveRetornar200ComResultadosOrdenados() throws Exception {
            when(pipelineService.compararFormacoes(anyList(), isNull()))
                    .thenReturn(List.of(
                            resultadoMock(new FormacaoConfig(4, 3, 3), 90.0),
                            resultadoMock(new FormacaoConfig(3, 4, 3), 95.0)));

            mockMvc.perform(get("/api/time/comparar").param("formacoes", "4-3-3,3-4-3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rodada").value(15))
                    .andExpect(jsonPath("$.formacoesComparadas").value(2))
                    .andExpect(jsonPath("$.melhorFormacao").value("3-4-3"))
                    .andExpect(jsonPath("$.resultados[0].formacao").value("3-4-3"))
                    .andExpect(jsonPath("$.resultados[0].posicao").value(1))
                    .andExpect(jsonPath("$.resultados[0].scoreTotal").value(95.0))
                    .andExpect(jsonPath("$.resultados[0].time").exists())
                    .andExpect(jsonPath("$.resultados[1].formacao").value("4-3-3"))
                    .andExpect(jsonPath("$.resultados[1].posicao").value(2));
        }

        @Test
        @DisplayName("deve retornar 400 quando apenas 1 formacao e informada")
        void deveRetornar400ComUmaFormacao() throws Exception {
            mockMvc.perform(get("/api/time/comparar").param("formacoes", "4-3-3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("ao menos 2")));

            verify(pipelineService, never()).compararFormacoes(any(), any());
        }

        @Test
        @DisplayName("deve retornar 400 quando uma formacao tem soma de linhas invalida")
        void deveRetornar400ComFormacaoInvalida() throws Exception {
            mockMvc.perform(get("/api/time/comparar").param("formacoes", "4-3-2,3-4-3"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("4-3-2")));

            verify(pipelineService, never()).compararFormacoes(any(), any());
        }

        @Test
        @DisplayName("deve ignorar formacoes duplicadas e comparar apenas as distintas")
        @SuppressWarnings("unchecked")
        void deveIgnorarDuplicadas() throws Exception {
            when(pipelineService.compararFormacoes(anyList(), isNull()))
                    .thenReturn(List.of(
                            resultadoMock(new FormacaoConfig(4, 3, 3), 90.0),
                            resultadoMock(new FormacaoConfig(3, 4, 3), 95.0)));

            mockMvc.perform(get("/api/time/comparar").param("formacoes", "4-3-3,4-3-3,3-4-3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.formacoesComparadas").value(2));

            ArgumentCaptor<List<FormacaoConfig>> captor = ArgumentCaptor.forClass(List.class);
            verify(pipelineService).compararFormacoes(captor.capture(), isNull());
            assertThat(captor.getValue()).containsExactly(
                    new FormacaoConfig(4, 3, 3),
                    new FormacaoConfig(3, 4, 3));
        }

        @Test
        @DisplayName("deve retornar 400 quando o parametro formacoes nao e informado")
        void deveRetornar400SemParametro() throws Exception {
            mockMvc.perform(get("/api/time/comparar"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("obrigatorio")));

            verify(pipelineService, never()).compararFormacoes(any(), any());
        }

        @Test
        @DisplayName("deve retornar 400 quando orcamento for menor ou igual a zero")
        void deveRetornar400ComOrcamentoInvalido() throws Exception {
            mockMvc.perform(get("/api/time/comparar")
                            .param("formacoes", "4-3-3,3-4-3")
                            .param("orcamento", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(containsString("orcamento")));

            verify(pipelineService, never()).compararFormacoes(any(), any());
        }

        @Test
        @DisplayName("deve propagar orcamento para o pipeline quando informado")
        void devePropagarOrcamento() throws Exception {
            when(pipelineService.compararFormacoes(anyList(), eq(120.0)))
                    .thenReturn(List.of(
                            resultadoMock(new FormacaoConfig(4, 3, 3), 90.0),
                            resultadoMock(new FormacaoConfig(3, 4, 3), 95.0)));

            mockMvc.perform(get("/api/time/comparar")
                            .param("formacoes", "4-3-3,3-4-3")
                            .param("orcamento", "120.0"))
                    .andExpect(status().isOk());

            verify(pipelineService).compararFormacoes(anyList(), eq(120.0));
        }

        @Test
        @DisplayName("deve expor formacaoCompleta em cada resultado")
        void deveExporFormacaoCompleta() throws Exception {
            when(pipelineService.compararFormacoes(anyList(), isNull()))
                    .thenReturn(List.of(
                            resultadoMock(new FormacaoConfig(4, 3, 3), 90.0),
                            resultadoMock(new FormacaoConfig(3, 4, 3), 95.0)));

            mockMvc.perform(get("/api/time/comparar").param("formacoes", "4-3-3,3-4-3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultados[0].formacaoCompleta").value(true))
                    .andExpect(jsonPath("$.resultados[1].formacaoCompleta").value(true));
        }

        @Test
        @DisplayName("nao deve persistir escalacao ao comparar formacoes")
        void naoDevePersistirEscalacao() throws Exception {
            when(pipelineService.compararFormacoes(anyList(), isNull()))
                    .thenReturn(List.of(
                            resultadoMock(new FormacaoConfig(4, 3, 3), 90.0),
                            resultadoMock(new FormacaoConfig(3, 4, 3), 95.0)));

            mockMvc.perform(get("/api/time/comparar").param("formacoes", "4-3-3,3-4-3"))
                    .andExpect(status().isOk());

            verify(escalacaoService, never()).salvarEscalacao(any(), any());
        }
    }

    private ResultadoFormacao resultadoMock(FormacaoConfig formacao, double scoreCapitao) {
        var capitao = Atleta.builder()
                .atletaId(1).apelido("Hulk").posicao(Posicao.ATA)
                .clubeId(1).nomeClube("Atletico Mineiro").siglaClube("ATM").nomeClubeNorm("atm")
                .status(StatusAtleta.PROVAVEL).mediaPontos(9.5).valorizacao(3.2)
                .preco(22.0).desempenhoRecente(0.0).score(scoreCapitao).build();
        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        titulares.put(Posicao.ATA, List.of(capitao));
        var time = Time.builder().rodada(15).avisoMercado(null)
                .titulares(titulares).reservas(Map.of()).capitao(capitao)
                .reservaLuxo(null).alertasDuvida(List.of()).custoTotal(120.0)
                .estrategia(com.cartola.odds.model.enums.Estrategia.SCORE_MAXIMO)
                .formacaoCompleta(true).build();
        return new ResultadoFormacao(formacao, time);
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
        return timeComOrcamento(orcamento, custoTotal, true, null);
    }

    private Time criarTimeMockIncompleto(double orcamento, double custoTotal) {
        return timeComOrcamento(orcamento, custoTotal, false,
                "Orcamento de C$30,0 insuficiente para completar a formacao "
                        + "(10/12 titulares escalados). Considere aumentar o orcamento.");
    }

    private Time timeComOrcamento(double orcamento, double custoTotal,
                                  boolean formacaoCompleta, String avisoOrcamento) {
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
                .estrategia(com.cartola.odds.model.enums.Estrategia.SCORE_MAXIMO)
                .formacaoCompleta(formacaoCompleta)
                .avisoOrcamento(avisoOrcamento)
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
                .formacaoCompleta(true)
                .build();
    }
}
