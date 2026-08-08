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
import org.mockito.ArgumentCaptor;
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
            configurarMocks(atletas, Set.of("fla"), Set.of(1), Map.of(1, desemp(8.0)));

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
            verify(cartolaDataService, times(1)).buscarDadosRodada();
            verify(oddsService,        times(1)).buscarFavoritos(any());
            verify(cartolaDataService, times(1)).buscarAtletasFiltrados(any());
            verify(desempenhoService,  times(1)).calcularDesempenhoUltimasRodadas(15);
            verify(scoreService,       times(1)).calcularScores(any(), any(), any(), any());
            verify(montadorTimeService,times(1)).montar(any(), eq(15), any(), isNull());
        }

        @Test
        @DisplayName("deve passar rodadaAtual=15 para DesempenhoService")
        void devePassarRodadaParaDesempenho() {
            var atletas = atletasMinimos();
            configurarMocks(atletas, Set.of(), Set.of(), Map.of());

            pipelineService.executar();

            verify(desempenhoService).calcularDesempenhoUltimasRodadas(15);
        }

        @Test
        @DisplayName("deve passar desempenhoMap ao ScoreService")
        void devePassarDesempenhoMapAoScore() {
            var atletas        = atletasMinimos();
            var favoritos      = Set.of("fla");
            var timesCasa      = Set.of(1);
            var desempenhoMap  = Map.of(1, desemp(9.5));
            configurarMocks(atletas, favoritos, timesCasa, desempenhoMap);

            pipelineService.executar();

            verify(scoreService).calcularScores(atletas, timesCasa, favoritos, desempenhoMap);
        }

        @Test
        @DisplayName("deve lancar IllegalStateException quando pool estiver vazio")
        void deveLancarExcecaoQuandoPoolVazio() {
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(List.of());

            assertThatThrownBy(() -> pipelineService.executar())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nenhum atleta disponivel");
        }

        @Test
        @DisplayName("nao deve chamar DesempenhoService quando pool estiver vazio")
        void naoDeveChamarDesempenhoQuandoPoolVazio() {
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(List.of());

            try { pipelineService.executar(); } catch (IllegalStateException ignored) {}

            verify(desempenhoService, never()).calcularDesempenhoUltimasRodadas(anyInt());
            verify(scoreService,      never()).calcularScores(any(), any(), any(), any());
            verify(montadorTimeService, never()).montar(any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("deve continuar pipeline mesmo com mercado fechado")
        void deveContinuarComMercadoFechado() {
            var statusFechado = new MercadoStatusResponse();
            statusFechado.setStatusMercado(2);
            statusFechado.setRodadaAtual(14);

            var atletas = atletasMinimos();
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusFechado);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
            when(desempenhoService.calcularDesempenhoUltimasRodadas(14)).thenReturn(Map.of());
            when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
            when(montadorTimeService.montar(any(), eq(14), any(), any())).thenReturn(timeMock(14));

            var resultado = pipelineService.executar();

            assertThat(resultado.getRodada()).isEqualTo(14);
        }

        @Test
        @DisplayName("deve propagar o orcamento informado ao MontadorTimeService")
        void devePropagarOrcamentoAoMontador() {
            var atletas = atletasMinimos();
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
            when(desempenhoService.calcularDesempenhoUltimasRodadas(15)).thenReturn(Map.of());
            when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
            when(montadorTimeService.montar(any(), eq(15), any(), eq(120.0))).thenReturn(timeMock(15));

            pipelineService.executar(120.0);

            verify(montadorTimeService).montar(any(), eq(15), any(), eq(120.0));
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

    @Nested
    @DisplayName("executar — filtro excluirDuvida")
    class ExecutarExcluirDuvida {

        @Test
        @DisplayName("deve remover atletas em duvida do pool quando excluirDuvida=true")
        void deveRemoverAtletasEmDuvida() {
            var atletas = List.of(provavel(1, "Provavel"), duvida(2, "Duvidoso"));
            configurarMocks(atletas, Set.of("fla"), Set.of(1), Map.of());

            pipelineService.executar(null, true);

            ArgumentCaptor<List<Atleta>> captor = ArgumentCaptor.captor();
            verify(scoreService).calcularScores(captor.capture(), any(), any(), any());
            assertThat(captor.getValue())
                    .extracting(Atleta::getApelido)
                    .containsExactly("Provavel");
        }

        @Test
        @DisplayName("deve manter atletas em duvida no pool quando excluirDuvida=false")
        void deveManterAtletasEmDuvida() {
            var atletas = List.of(provavel(1, "Provavel"), duvida(2, "Duvidoso"));
            configurarMocks(atletas, Set.of("fla"), Set.of(1), Map.of());

            pipelineService.executar(null, false);

            verify(scoreService).calcularScores(eq(atletas), any(), any(), any());
        }

        @Test
        @DisplayName("deve remover em duvida mesmo quando tem score maior que os provaveis")
        void deveRemoverEmDuvidaComScoreMaior() {
            var provavel  = provavel(1, "Provavel").withScore(5.0);
            var duvidaTop = duvida(2, "DuvidaTop").withScore(99.0);
            var atletas   = List.of(provavel, duvidaTop);
            configurarMocks(atletas, Set.of("fla"), Set.of(1), Map.of());

            pipelineService.executar(null, true);

            ArgumentCaptor<List<Atleta>> captor = ArgumentCaptor.captor();
            verify(scoreService).calcularScores(captor.capture(), any(), any(), any());
            assertThat(captor.getValue()).doesNotContain(duvidaTop);
        }

        @Test
        @DisplayName("deve montar time sem erro quando todos os candidatos estao em duvida")
        void deveMontarTimeQuandoTodosEmDuvida() {
            var atletas = List.of(duvida(1, "Duvidoso"));
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
            when(desempenhoService.calcularDesempenhoUltimasRodadas(15)).thenReturn(Map.of());
            when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(List.of());
            when(montadorTimeService.montar(any(), eq(15), any(), isNull())).thenReturn(timeMock(15));

            var resultado = pipelineService.executar(null, true);

            assertThat(resultado).isNotNull();
            verify(scoreService).calcularScores(eq(List.of()), any(), any(), any());
        }

        @Test
        @DisplayName("deve combinar excluirDuvida com o orcamento informado")
        void deveCombinarComOrcamento() {
            var atletas = List.of(provavel(1, "Provavel"), duvida(2, "Duvidoso"));
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
            when(desempenhoService.calcularDesempenhoUltimasRodadas(15)).thenReturn(Map.of());
            when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
            when(montadorTimeService.montar(any(), eq(15), any(), eq(120.0))).thenReturn(timeMock(15));

            pipelineService.executar(120.0, true);

            ArgumentCaptor<List<Atleta>> captor = ArgumentCaptor.captor();
            verify(scoreService).calcularScores(captor.capture(), any(), any(), any());
            assertThat(captor.getValue()).extracting(Atleta::getApelido).containsExactly("Provavel");
            verify(montadorTimeService).montar(any(), eq(15), any(), eq(120.0));
        }

        @Test
        @DisplayName("deve usar excluirDuvida=false na sobrecarga sem o parametro")
        void deveUsarPadraoFalseNaSobrecarga() {
            var atletas = List.of(provavel(1, "Provavel"), duvida(2, "Duvidoso"));
            configurarMocks(atletas, Set.of(), Set.of(), Map.of());

            pipelineService.executar(null);

            verify(scoreService).calcularScores(eq(atletas), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("compararFormacoes")
    class CompararFormacoes {

        @Test
        @DisplayName("deve montar um time por formacao reaproveitando o mesmo pool")
        void deveMontarUmTimePorFormacao() {
            var atletas = atletasMinimos();
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
            when(desempenhoService.calcularDesempenhoUltimasRodadas(15)).thenReturn(Map.of());
            when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
            when(montadorTimeService.montar(any(), eq(15), any(), isNull(), any()))
                    .thenReturn(timeMock(15));

            var formacoes = List.of(
                    new com.cartola.odds.model.FormacaoConfig(4, 3, 3),
                    new com.cartola.odds.model.FormacaoConfig(3, 4, 3));

            var resultados = pipelineService.compararFormacoes(formacoes, null);

            assertThat(resultados).hasSize(2);
            assertThat(resultados).extracting(r -> r.formacao()).containsExactlyElementsOf(formacoes);

            // O pool e preparado uma unica vez, independente da quantidade de formacoes
            verify(cartolaDataService, times(1)).buscarStatusMercado();
            verify(scoreService, times(1)).calcularScores(any(), any(), any(), any());
            verify(montadorTimeService, times(1))
                    .montar(any(), eq(15), any(), isNull(), eq(new com.cartola.odds.model.FormacaoConfig(4, 3, 3)));
            verify(montadorTimeService, times(1))
                    .montar(any(), eq(15), any(), isNull(), eq(new com.cartola.odds.model.FormacaoConfig(3, 4, 3)));
        }

        @Test
        @DisplayName("deve propagar o orcamento a cada montagem de formacao")
        void devePropagarOrcamento() {
            var atletas = atletasMinimos();
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
            when(desempenhoService.calcularDesempenhoUltimasRodadas(15)).thenReturn(Map.of());
            when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
            when(montadorTimeService.montar(any(), eq(15), any(), eq(120.0), any()))
                    .thenReturn(timeMock(15));

            pipelineService.compararFormacoes(List.of(
                    new com.cartola.odds.model.FormacaoConfig(4, 3, 3),
                    new com.cartola.odds.model.FormacaoConfig(3, 4, 3)), 120.0);

            verify(montadorTimeService, times(2)).montar(any(), eq(15), any(), eq(120.0), any());
        }

        @Test
        @DisplayName("deve lancar IllegalStateException quando o pool estiver vazio")
        void deveLancarExcecaoQuandoPoolVazio() {
            when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
            when(cartolaDataService.buscarDadosRodada())
                    .thenReturn(new CartolaDataService.DadosRodada(Set.of(), Set.of()));
            when(oddsService.buscarFavoritos(any())).thenReturn(Set.of());
            when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(List.of());

            assertThatThrownBy(() -> pipelineService.compararFormacoes(
                    List.of(new com.cartola.odds.model.FormacaoConfig(4, 3, 3),
                            new com.cartola.odds.model.FormacaoConfig(3, 4, 3)), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nenhum atleta disponivel");

            verify(montadorTimeService, never()).montar(any(), anyInt(), any(), any(), any());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private DesempenhoService.DesempenhoAtleta desemp(double media) {
        return new DesempenhoService.DesempenhoAtleta(media, 0.0, DesempenhoService.RODADAS_HISTORICO);
    }

    private void configurarMocks(List<Atleta> atletas, Set<String> favoritos,
                                  Set<Integer> timesCasa,
                                  Map<Integer, DesempenhoService.DesempenhoAtleta> desempenhoMap) {
        when(cartolaDataService.buscarStatusMercado()).thenReturn(statusRodada15);
        when(cartolaDataService.buscarDadosRodada())
                .thenReturn(new CartolaDataService.DadosRodada(timesCasa, Set.of()));
        when(oddsService.buscarFavoritos(any())).thenReturn(favoritos);
        when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(atletas);
        when(desempenhoService.calcularDesempenhoUltimasRodadas(15)).thenReturn(desempenhoMap);
        when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(atletas);
        when(montadorTimeService.montar(any(), eq(15), any(), isNull())).thenReturn(timeMock(15));
    }

    private List<Atleta> atletasMinimos() {
        return List.of(provavel(1, "Jogador"));
    }

    private Atleta provavel(int atletaId, String apelido) {
        return atleta(atletaId, apelido, StatusAtleta.PROVAVEL);
    }

    private Atleta duvida(int atletaId, String apelido) {
        return atleta(atletaId, apelido, StatusAtleta.DUVIDA);
    }

    private Atleta atleta(int atletaId, String apelido, StatusAtleta status) {
        return Atleta.builder()
                .atletaId(atletaId).apelido(apelido).posicao(Posicao.ATA)
                .clubeId(1).nomeClube("Fla").siglaClube("FLA").nomeClubeNorm("fla")
                .status(status).mediaPontos(8.0).valorizacao(2.0)
                .preco(15.0).desempenhoRecente(0.0).score(5.0).build();
    }

    private Time timeMock(int rodada) {
        return Time.builder()
                .rodada(rodada)
                .avisoMercado(null)
                .titulares(new EnumMap<>(Posicao.class))
                .reservas(Map.of()).alertasDuvida(List.of()).custoTotal(0.0).build();
    }
}
