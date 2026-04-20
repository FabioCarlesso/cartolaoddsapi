package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.model.response.MercadoStatusResponse;
import com.cartola.odds.service.DesempenhoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingService")
class RankingServiceTest {

    @Mock OddsService        oddsService;
    @Mock CartolaDataService cartolaDataService;
    @Mock DesempenhoService   desempenhoService;
    @Mock ScoreService        scoreService;

    @InjectMocks RankingService rankingService;

    private MercadoStatusResponse statusPadrao;

    @BeforeEach
    void setUp() {
        statusPadrao = new MercadoStatusResponse();
        statusPadrao.setStatusMercado(1);
        statusPadrao.setRodadaAtual(15);

        when(oddsService.buscarFavoritos()).thenReturn(Set.of("flamengo"));
        when(cartolaDataService.buscarStatusMercado()).thenReturn(statusPadrao);
        when(cartolaDataService.buscarTimesCasa()).thenReturn(Set.of(1));
    }

    @Nested
    @DisplayName("buscarRanking — ordenacao e limite")
    class OrdenacaoELimite {

        @Test
        @DisplayName("deve retornar atletas ordenados por score decrescente")
        void deveOrdenarPorScoreDecrescente() {
            var pool = List.of(
                atleta(Posicao.ATA, 5.0),
                atleta(Posicao.ATA, 9.0),
                atleta(Posicao.ATA, 3.0),
                atleta(Posicao.ATA, 7.0)
            );
            configurarPool(pool);

            var resultado = rankingService.buscarRanking(null, 25);

            var scores = resultado.getAtletas().stream()
                    .mapToDouble(a -> a.getScore()).toArray();
            assertThat(scores).containsExactly(9.0, 7.0, 5.0, 3.0);
        }

        @Test
        @DisplayName("deve numerar posicoes do ranking a partir de 1")
        void deveNumerarRankingAPartirDeUm() {
            configurarPool(List.of(atleta(Posicao.ATA, 8.0), atleta(Posicao.ATA, 6.0)));

            var resultado = rankingService.buscarRanking(null, 25);

            assertThat(resultado.getAtletas().get(0).getRank()).isEqualTo(1);
            assertThat(resultado.getAtletas().get(1).getRank()).isEqualTo(2);
        }

        @Test
        @DisplayName("deve respeitar o limite informado")
        void deveRespeitarOLimite() {
            configurarPool(criarAtletas(30, Posicao.ATA));

            var resultado = rankingService.buscarRanking(null, 10);

            assertThat(resultado.getAtletas()).hasSize(10);
            assertThat(resultado.getLimite()).isEqualTo(10);
        }

        @Test
        @DisplayName("deve retornar 25 atletas com limite padrao")
        void deveRetornar25ComLimitePadrao() {
            configurarPool(criarAtletas(40, Posicao.ATA));

            var resultado = rankingService.buscarRanking(null, RankingService.LIMITE_PADRAO);

            assertThat(resultado.getAtletas()).hasSize(25);
        }

        @Test
        @DisplayName("nao deve ultrapassar LIMITE_MAXIMO de 100")
        void naoDeveUltrapassarLimiteMaximo() {
            configurarPool(criarAtletas(150, Posicao.ATA));

            var resultado = rankingService.buscarRanking(null, 999);

            assertThat(resultado.getAtletas()).hasSize(RankingService.LIMITE_MAXIMO);
            assertThat(resultado.getLimite()).isEqualTo(RankingService.LIMITE_MAXIMO);
        }

        @ParameterizedTest(name = "limite={0} deve ser tratado como 1")
        @ValueSource(ints = {0, -1, -99})
        @DisplayName("deve tratar limite <= 0 como 1")
        void deveTratarLimiteNegativoComoUm(int limiteInvalido) {
            configurarPool(criarAtletas(10, Posicao.ATA));

            var resultado = rankingService.buscarRanking(null, limiteInvalido);

            assertThat(resultado.getAtletas()).hasSize(1);
            assertThat(resultado.getLimite()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve informar totalDisponivel com o pool completo antes do limite")
        void deveInformarTotalDisponivel() {
            configurarPool(criarAtletas(40, Posicao.ATA));

            var resultado = rankingService.buscarRanking(null, 10);

            assertThat(resultado.getTotalDisponivel()).isEqualTo(40);
            assertThat(resultado.getAtletas()).hasSize(10);
        }
    }

    @Nested
    @DisplayName("buscarRanking — filtro por posicao")
    class FiltroPorPosicao {

        @Test
        @DisplayName("deve retornar somente atletas da posicao filtrada")
        void deveRetornarSomentePosicaoFiltrada() {
            var pool = List.of(
                atleta(Posicao.ATA, 9.0),
                atleta(Posicao.MEI, 8.0),
                atleta(Posicao.ATA, 7.0),
                atleta(Posicao.GOL, 10.0)
            );
            configurarPool(pool);

            var resultado = rankingService.buscarRanking(Posicao.ATA, 25);

            assertThat(resultado.getAtletas()).hasSize(2);
            assertThat(resultado.getAtletas())
                    .allMatch(a -> "ATA".equals(a.getPosicao()));
        }

        @Test
        @DisplayName("deve retornar todas as posicoes quando posicao for null")
        void deveRetornarTodasQuandoPosicaoNull() {
            var pool = List.of(
                atleta(Posicao.ATA, 9.0),
                atleta(Posicao.MEI, 8.0),
                atleta(Posicao.GOL, 7.0)
            );
            configurarPool(pool);

            var resultado = rankingService.buscarRanking(null, 25);

            assertThat(resultado.getAtletas()).hasSize(3);
            assertThat(resultado.getPosicao()).isEqualTo("TODAS");
        }

        @Test
        @DisplayName("deve retornar lista vazia quando nenhum atleta bate a posicao filtrada")
        void deveRetornarVazioQuandoNenhumNaPosicao() {
            configurarPool(List.of(atleta(Posicao.GOL, 8.0)));

            var resultado = rankingService.buscarRanking(Posicao.ATA, 25);

            assertThat(resultado.getAtletas()).isEmpty();
            assertThat(resultado.getTotalDisponivel()).isEqualTo(0);
        }

        @Test
        @DisplayName("deve informar a sigla correta da posicao no response")
        void deveInformarSiglaDaPosicao() {
            configurarPool(List.of(atleta(Posicao.MEI, 8.0)));

            var resultado = rankingService.buscarRanking(Posicao.MEI, 25);

            assertThat(resultado.getPosicao()).isEqualTo("MEI");
        }
    }

    @Nested
    @DisplayName("buscarRanking — metadados da resposta")
    class Metadados {

        @Test
        @DisplayName("deve preencher numero da rodada corretamente")
        void devePreencherRodada() {
            statusPadrao.setRodadaAtual(22);
            configurarPool(List.of(atleta(Posicao.ATA, 8.0)));

            var resultado = rankingService.buscarRanking(null, 25);

            assertThat(resultado.getRodada()).isEqualTo(22);
        }

        @Test
        @DisplayName("deve marcar emDuvida=true para atleta com status DUVIDA")
        void deveMarcareEmDuvidaVerdadeiro() {
            var atletaDuvida = Atleta.builder()
                    .apelido("AtletaDuvida")
                    .posicao(Posicao.ATA)
                    .clubeId(1).nomeClube("Flamengo").siglaClube("FLA")
                    .nomeClubeNorm("flamengo").status(StatusAtleta.DUVIDA)
                    .mediaPontos(8.0).valorizacao(2.0).preco(15.0).score(8.0)
                    .build();
            configurarPool(List.of(atletaDuvida));

            var resultado = rankingService.buscarRanking(null, 25);

            assertThat(resultado.getAtletas().get(0).isEmDuvida()).isTrue();
        }

        @Test
        @DisplayName("deve marcar emDuvida=false para atleta provavel")
        void deveMarcareEmDuvidaFalso() {
            configurarPool(List.of(atleta(Posicao.ATA, 8.0)));

            var resultado = rankingService.buscarRanking(null, 25);

            assertThat(resultado.getAtletas().get(0).isEmDuvida()).isFalse();
        }

        @Test
        @DisplayName("deve preencher todos os campos do atleta no ranking")
        void devePreencherCamposDoAtleta() {
            var a = Atleta.builder()
                    .apelido("Hulk").posicao(Posicao.ATA).clubeId(1)
                    .nomeClube("Atletico Mineiro").siglaClube("ATM")
                    .nomeClubeNorm("atletico mineiro").status(StatusAtleta.PROVAVEL)
                    .mediaPontos(9.5).valorizacao(3.2).preco(22.0).score(8.54)
                    .build();
            configurarPool(List.of(a));

            var dto = rankingService.buscarRanking(null, 25).getAtletas().get(0);

            assertThat(dto.getApelido()).isEqualTo("Hulk");
            assertThat(dto.getSiglaClube()).isEqualTo("ATM");
            assertThat(dto.getNomeClube()).isEqualTo("Atletico Mineiro");
            assertThat(dto.getPosicao()).isEqualTo("ATA");
            assertThat(dto.getPreco()).isEqualTo(22.0);
            assertThat(dto.getScore()).isEqualTo(8.54);
            assertThat(dto.getFormatado()).isEqualTo("Hulk (ATM)");
            assertThat(dto.getRank()).isEqualTo(1);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void configurarPool(List<Atleta> pool) {
        when(cartolaDataService.buscarAtletasFiltrados(any())).thenReturn(pool);
        when(desempenhoService.calcularMediaUltimasRodadas(anyInt())).thenReturn(java.util.Map.of());
        when(scoreService.calcularScores(any(), any(), any(), any())).thenReturn(pool);
    }

    private Atleta atleta(Posicao posicao, double score) {
        return Atleta.builder()
                .atletaId((int) score * 10)
                .apelido(posicao.name() + "_" + (int) score)
                .posicao(posicao)
                .clubeId(1)
                .nomeClube("Flamengo")
                .siglaClube("FLA")
                .nomeClubeNorm("flamengo")
                .status(StatusAtleta.PROVAVEL)
                .mediaPontos(score)
                .valorizacao(1.0)
                .preco(15.0)
                .desempenhoRecente(0.0)
                .score(score)
                .build();
    }

    private List<Atleta> criarAtletas(int qtd, Posicao posicao) {
        var lista = new ArrayList<Atleta>();
        for (int i = 0; i < qtd; i++) {
            lista.add(atleta(posicao, qtd - i));
        }
        return lista;
    }
}
