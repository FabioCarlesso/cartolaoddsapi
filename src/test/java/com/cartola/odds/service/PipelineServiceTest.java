package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.model.response.MercadoStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineService")
class PipelineServiceTest {

    @Mock OddsService         oddsService;
    @Mock CartolaDataService  cartolaDataService;
    @Mock DesempenhoService   desempenhoService;
    @Mock ScoreService        scoreService;
    @Mock MontadorTimeService montadorTimeService;

    @InjectMocks PipelineService pipelineService;

    private MercadoStatusResponse statusRodada15;

    @BeforeEach
    void setUp() {
        statusRodada15 = new MercadoStatusResponse();
        statusRodada15.setStatusMercado(1);
        statusRodada15.setRodadaAtual(15);
    }

    @Nested
    @DisplayName("executar")
    class Executar {

        @Test
        @DisplayName("deve executar pipeline completo e retornar Time preenchido")
        void deveExecutarPipelineCompleto() {
            var atletas = atletasMinimos();
            configurarMocks(atletas, Set.of("fla"), Set.of(1), Map.of(1, 8.0));

            var resultado = pipelineService.executar();

            assertThat(resultado).isNotNull();
            assertThat(resultado.getRodada()).isEqualTo(15);
        }

        @Test
        @DisplayName("deve chamar cada etapa exatamente uma vez")
        void deveChamarCadaEtapaUmaVez() {
            var atletas = atletasMinimos();
            configurarMocks(atletas, Set.of("fla"), Set.of(1), Map.of());

            pipelineService.executar();

            verify(cartolaDataService, times(1)).buscarStatusMercado();
            verify(oddsService,        times(1)).buscarFavoritos();
            verify(cartolaDataService, times(1)).buscarAtletasFiltrados(any());
            verify(cartolaDataService, times(1)).buscarTimesCasa();
            verify(desempenhoService,  times(1)).calcularMediaUltimasRodadas(15);
            verify(scoreService,       times(1)).calcularScores(any(), any(), any(), any());
            verify(montadorTimeService,times(1)).montar(any(), eq(15), any());
        }

        @Test
        @DisplayName("deve passar rodadaAtual=15 para DesempenhoService")
        void devePassarRodadaParaDesempenho() {
            var atletas = atletasMinimos();
            configurarMocks(atletas, Set.of(), Set.of(), Map.of());

            pipelineService.executar();

            verify(desempenhoService).calcularMediaUltimasRodadas(15);
        }

        @Test
        @DisplayName("deve passar desempenhoMap ao ScoreService")
        void devePassarDesempenhoMapAoScore() {
            var atletas        = atletasMinimos();
            var favoritos      = Set.of("fla");
            var timesCasa      = Set.of(1);
            var desempenhoMap  = Map.of(1, 9.5);
            configurarMocks(atletas, favoritos, timesCasa, desempenhoMap);

            pipelineService.executar();

            verify(scoreService).calcularScores(atletas, timesCasa, favoritos, desempenhoMap);
        }

        @Test
        @DisplayName("deve lancar IllegalStateException quando pool estiver vazio")
        void deveLancarExcecaoQuandoPoolVazio() {
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(oddsService.buscarFavoritos()).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(List.of());

            assertThatThrownBy(() -> pipelineService.executar())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nenhum atleta disponivel");
        }

        @Test
        @DisplayName("nao deve chamar DesempenhoService quando pool estiver vazio")
        void naoDeveChamarDesempenhoQuandoPoolVazio() {
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(oddsService.buscarFavoritos()).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(List.of());

            try { pipelineService.executar(); } catch (IllegalStateException ignored) {}

            verify(desempenhoService, never()).calcularMediaUltimasRodadas(anyInt());
            verify(scoreService,      never()).calcularScores(any(), any(), any(), any());
            verify(montadorTimeService, never()).montar(any(), anyInt(), any());
        }

        @Test
        @DisplayName("deve continuar pipeline mesmo com mercado fechado")
        void deveContinuarComMercadoFechado() {
            var statusFechado = new MercadoStatusResponse();
            statusFechado.setStatusMercado(2);
            statusFechado.setRodadaAtual(14);

            var atletas = atletasMinimos();
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusFechado);
            when(oddsService.buscarFavoritos()).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
            when(cartolaDataService.buscarTimesCasa()).thenReturn(Set.of());
            when(desempenhoService.calcularMediaUltimasRodadas(14)).thenReturn(Map.of());
            when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
            when(montadorTimeService.montar(any(), eq(14), any())).thenReturn(timeMock(14));

            var resultado = pipelineService.executar();

            assertThat(resultado.getRodada()).isEqualTo(14);
        }

        @Test
        @DisplayName("deve funcionar com desempenhoMap vazio (historico indisponivel)")
        void deveFuncionarComDesempenhoMapVazio() {
            var atletas = atletasMinimos();
            configurarMocks(atletas, Set.of(), Set.of(), Map.of());

            var resultado = pipelineService.executar();

            assertThat(resultado).isNotNull();
            verify(scoreService).calcularScores(any(), any(), any(), eq(Map.of()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void configurarMocks(List<Atleta> atletas, Set<String> favoritos,
                                  Set<Integer> timesCasa, Map<Integer, Double> desempenhoMap) {
        when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
        when(oddsService.buscarFavoritos()).thenReturn(favoritos);
        when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
        when(cartolaDataService.buscarTimesCasa()).thenReturn(timesCasa);
        when(desempenhoService.calcularMediaUltimasRodadas(15)).thenReturn(desempenhoMap);
        when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
        when(montadorTimeService.montar(any(), eq(15), any())).thenReturn(timeMock(15));
    }

    private List<Atleta> atletasMinimos() {
        return List.of(Atleta.builder()
                .atletaId(1).apelido("Jogador").posicao(Posicao.ATA)
                .clubeId(1).nomeClube("Fla").siglaClube("FLA").nomeClubeNorm("fla")
                .status(StatusAtleta.PROVAVEL).mediaPontos(8.0).valorizacao(2.0)
                .preco(15.0).desempenhoRecente(0.0).score(5.0).build());
    }

    private Time timeMock(int rodada) {
        return Time.builder()
                .rodada(rodada)
                .avisoMercado(null)
                .titulares(new EnumMap<>(Posicao.class))
                .reservas(Map.of()).alertasDuvida(List.of()).custoTotal(0.0).build();
    }
}
