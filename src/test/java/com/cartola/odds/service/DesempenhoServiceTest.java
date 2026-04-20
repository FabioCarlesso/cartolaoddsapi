package com.cartola.odds.service;

import com.cartola.odds.client.CartolaClient;
import com.cartola.odds.model.response.PontuadosResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DesempenhoService")
class DesempenhoServiceTest {

    @Mock  CartolaClient cartolaClient;
    @InjectMocks DesempenhoService service;

    @Nested
    @DisplayName("calcularMediaUltimasRodadas")
    class CalcularMedia {

        @Test
        @DisplayName("deve retornar mapa vazio quando rodadaAtual for 1 (sem historico)")
        void deveRetornarVazioSemHistorico() {
            var resultado = service.calcularMediaUltimasRodadas(1);
            assertThat(resultado).isEmpty();
            verifyNoInteractions(cartolaClient);
        }

        @Test
        @DisplayName("deve calcular media correta de um atleta com 2 rodadas disponíveis")
        void deveCalcularMediaComDuasRodadas() {
            // rodadaAtual=3 -> busca rodadas 1 e 2
            when(cartolaClient.buscarPontuados(1)).thenReturn(pontuados("10", 6.0));
            when(cartolaClient.buscarPontuados(2)).thenReturn(pontuados("10", 8.0));

            var resultado = service.calcularMediaUltimasRodadas(3);

            assertThat(resultado).containsKey(10);
            assertThat(resultado.get(10)).isCloseTo(7.0, within(0.001)); // (6+8)/2
        }

        @Test
        @DisplayName("deve calcular media correta das ultimas 5 rodadas")
        void deveCalcularMediaDasUltimas5Rodadas() {
            // rodadaAtual=7 -> busca rodadas 2,3,4,5,6
            when(cartolaClient.buscarPontuados(2)).thenReturn(pontuados("5", 4.0));
            when(cartolaClient.buscarPontuados(3)).thenReturn(pontuados("5", 6.0));
            when(cartolaClient.buscarPontuados(4)).thenReturn(pontuados("5", 8.0));
            when(cartolaClient.buscarPontuados(5)).thenReturn(pontuados("5", 10.0));
            when(cartolaClient.buscarPontuados(6)).thenReturn(pontuados("5", 7.0));

            var resultado = service.calcularMediaUltimasRodadas(7);

            // (4+6+8+10+7)/5 = 35/5 = 7.0
            assertThat(resultado.get(5)).isCloseTo(7.0, within(0.001));
        }

        @Test
        @DisplayName("deve buscar no maximo RODADAS_HISTORICO rodadas")
        void deveBuscarNoMaximoRodadasHistorico() {
            // rodadaAtual=20 -> deve buscar somente as 5 ultimas (15..19)
            for (int r = 15; r < 20; r++) {
                when(cartolaClient.buscarPontuados(r)).thenReturn(pontuados("1", 5.0));
            }

            service.calcularMediaUltimasRodadas(20);

            for (int r = 15; r < 20; r++) {
                verify(cartolaClient, times(1)).buscarPontuados(r);
            }
            // rodadas anteriores a 15 nao devem ser buscadas
            verify(cartolaClient, never()).buscarPontuados(14);
            verify(cartolaClient, never()).buscarPontuados(1);
        }

        @Test
        @DisplayName("deve ignorar rodada que retorna null sem lancar excecao")
        void deveIgnorarRodadaNula() {
            when(cartolaClient.buscarPontuados(1)).thenReturn(null);
            when(cartolaClient.buscarPontuados(2)).thenReturn(pontuados("3", 9.0));

            var resultado = service.calcularMediaUltimasRodadas(3);

            // apenas a rodada 2 contribuiu
            assertThat(resultado).containsKey(3);
            assertThat(resultado.get(3)).isCloseTo(9.0, within(0.001));
        }

        @Test
        @DisplayName("deve ignorar rodada sem atletas no response")
        void deveIgnorarRodadaSemAtletas() {
            var semAtletas = new PontuadosResponse();
            semAtletas.setAtletas(null);
            when(cartolaClient.buscarPontuados(1)).thenReturn(semAtletas);

            var resultado = service.calcularMediaUltimasRodadas(2);

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("deve ignorar atleta com pontuacaoNum nula")
        void deveIgnorarAtletaSemPontuacao() {
            var pontuado = new PontuadosResponse.AtletaPontuado();
            pontuado.setPontuacaoNum(null);
            var resp = new PontuadosResponse();
            resp.setAtletas(Map.of("99", pontuado));
            when(cartolaClient.buscarPontuados(1)).thenReturn(resp);

            var resultado = service.calcularMediaUltimasRodadas(2);

            assertThat(resultado).doesNotContainKey(99);
        }

        @Test
        @DisplayName("deve calcular medias independentes para multiplos atletas")
        void deveCalcularMediasParaMultiplosAtletas() {
            var resp = new PontuadosResponse();
            var p1 = new PontuadosResponse.AtletaPontuado(); p1.setPontuacaoNum(10.0);
            var p2 = new PontuadosResponse.AtletaPontuado(); p2.setPontuacaoNum(4.0);
            resp.setAtletas(Map.of("1", p1, "2", p2));
            when(cartolaClient.buscarPontuados(1)).thenReturn(resp);

            var resultado = service.calcularMediaUltimasRodadas(2);

            assertThat(resultado.get(1)).isCloseTo(10.0, within(0.001));
            assertThat(resultado.get(2)).isCloseTo(4.0,  within(0.001));
        }

        @Test
        @DisplayName("deve calcular media mesmo quando atleta aparece em apenas algumas rodadas")
        void deveCalcularMediaParaAtletaParcial() {
            // atleta 7 aparece so na rodada 2 (nao na 1)
            when(cartolaClient.buscarPontuados(1)).thenReturn(pontuados("99", 5.0));
            when(cartolaClient.buscarPontuados(2)).thenReturn(pontuados("7", 8.0));

            var resultado = service.calcularMediaUltimasRodadas(3);

            assertThat(resultado.get(7)).isCloseTo(8.0, within(0.001));
            assertThat(resultado.get(99)).isCloseTo(5.0, within(0.001));
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private PontuadosResponse pontuados(String atletaId, double pontuacao) {
        var pontuado = new PontuadosResponse.AtletaPontuado();
        pontuado.setPontuacaoNum(pontuacao);
        var resp = new PontuadosResponse();
        resp.setAtletas(Map.of(atletaId, pontuado));
        return resp;
    }
}
