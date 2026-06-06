package com.cartola.odds.controller;

import com.cartola.odds.exception.RecursoNaoEncontradoException;
import com.cartola.odds.model.response.EscalacaoRodadaResponse;
import com.cartola.odds.model.response.HistoricoResponse;
import com.cartola.odds.service.EscalacaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoricoController.class)
@DisplayName("HistoricoController")
class HistoricoControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EscalacaoService escalacaoService;

    @Nested
    @DisplayName("GET /api/historico")
    class Listar {

        @Test
        @DisplayName("deve retornar lista vazia com 200 quando nao ha rodadas")
        void deveRetornarVazio() throws Exception {
            when(escalacaoService.buscarHistorico())
                    .thenReturn(HistoricoResponse.builder().totalRodadas(0).rodadas(List.of()).build());

            mockMvc.perform(get("/api/historico"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRodadas").value(0))
                    .andExpect(jsonPath("$.rodadas").isArray())
                    .andExpect(jsonPath("$.rodadas").isEmpty());
        }

        @Test
        @DisplayName("deve retornar resumo das rodadas registradas")
        void deveRetornarResumo() throws Exception {
            var resumo = HistoricoResponse.RodadaResumo.builder()
                    .rodadaId(14).criadoEm(LocalDateTime.of(2025, 5, 10, 10, 30))
                    .totalAtletas(12).scoreSugeridoTotal(94.3)
                    .pontuacaoRealTotal(87.5).pontuacaoRealDisponivel(true)
                    .build();
            when(escalacaoService.buscarHistorico())
                    .thenReturn(HistoricoResponse.builder().totalRodadas(1).rodadas(List.of(resumo)).build());

            mockMvc.perform(get("/api/historico"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRodadas").value(1))
                    .andExpect(jsonPath("$.rodadas[0].rodadaId").value(14))
                    .andExpect(jsonPath("$.rodadas[0].totalAtletas").value(12))
                    .andExpect(jsonPath("$.rodadas[0].scoreSugeridoTotal").value(94.3))
                    .andExpect(jsonPath("$.rodadas[0].pontuacaoRealTotal").value(87.5))
                    .andExpect(jsonPath("$.rodadas[0].pontuacaoRealDisponivel").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/historico/{rodadaId}")
    class Detalhar {

        @Test
        @DisplayName("deve retornar atletas com detalhes da rodada")
        void deveRetornarDetalhe() throws Exception {
            var atleta = EscalacaoRodadaResponse.AtletaEscalado.builder()
                    .apelido("Hulk").posicao("ATA").clube("Atletico MG")
                    .scoreSugerido(9.2).pontuacaoReal(8.5)
                    .capitao(true).reservaLuxo(false).emDuvida(false)
                    .build();
            when(escalacaoService.buscarPorRodada(14))
                    .thenReturn(EscalacaoRodadaResponse.builder().rodadaId(14).atletas(List.of(atleta)).build());

            mockMvc.perform(get("/api/historico/14"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rodadaId").value(14))
                    .andExpect(jsonPath("$.atletas[0].apelido").value("Hulk"))
                    .andExpect(jsonPath("$.atletas[0].posicao").value("ATA"))
                    .andExpect(jsonPath("$.atletas[0].scoreSugerido").value(9.2))
                    .andExpect(jsonPath("$.atletas[0].pontuacaoReal").value(8.5))
                    .andExpect(jsonPath("$.atletas[0].capitao").value(true));
        }

        @Test
        @DisplayName("deve retornar 404 quando rodada nao tem escalacao")
        void deveRetornar404() throws Exception {
            when(escalacaoService.buscarPorRodada(99))
                    .thenThrow(new RecursoNaoEncontradoException("Nenhuma escalacao registrada para a rodada 99"));

            mockMvc.perform(get("/api/historico/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.erro").value("Recurso nao encontrado"));
        }
    }

    @Nested
    @DisplayName("POST /api/historico/{rodadaId}/atualizar-pontuacao")
    class AtualizarPontuacao {

        @Test
        @DisplayName("deve retornar 200 com pontuacoes atualizadas")
        void deveAtualizar() throws Exception {
            var atleta = EscalacaoRodadaResponse.AtletaEscalado.builder()
                    .apelido("Hulk").posicao("ATA").clube("Atletico MG")
                    .scoreSugerido(9.2).pontuacaoReal(8.5)
                    .capitao(true).reservaLuxo(false).emDuvida(false)
                    .build();
            when(escalacaoService.atualizarPontuacaoReal(14))
                    .thenReturn(EscalacaoRodadaResponse.builder().rodadaId(14).atletas(List.of(atleta)).build());

            mockMvc.perform(post("/api/historico/14/atualizar-pontuacao"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rodadaId").value(14))
                    .andExpect(jsonPath("$.atletas[0].pontuacaoReal").value(8.5));
        }

        @Test
        @DisplayName("deve retornar 404 quando rodada nao tem escalacao")
        void deveRetornar404() throws Exception {
            when(escalacaoService.atualizarPontuacaoReal(99))
                    .thenThrow(new RecursoNaoEncontradoException("Nenhuma escalacao registrada para a rodada 99"));

            mockMvc.perform(post("/api/historico/99/atualizar-pontuacao"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }
}
