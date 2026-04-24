package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreService")
class ScoreServiceTest {

    @Mock ConfiguracaoService configuracaoService;

    @InjectMocks ScoreService scoreService;

    @BeforeEach
    void setUp() {
        when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
    }

    @Nested
    @DisplayName("calcularScores — sem mapa de desempenho (proxy)")
    class SemDesempenhoReal {

        @Test
        @DisplayName("deve retornar score zero para atleta sem pontos nem bonus")
        void deveRetornarScoreZeroSemDados() {
            var atleta = base().mediaPontos(0.0).valorizacao(0.0).build();
            var resultado = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of());
            assertThat(resultado.get(0).getScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("deve usar mediaPontos como proxy de desempenho quando mapa vazio")
        void deveUsarProxyQuandoSemHistorico() {
            // media=10.0, peso media=0.40 + peso desempenho=0.20 = 0.60 -> 10*0.60 = 6.0
            var atleta = base().mediaPontos(10.0).valorizacao(0.0).build();
            var resultado = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of());
            assertThat(resultado.get(0).getScore()).isCloseTo(6.0, within(0.001));
        }

        @Test
        @DisplayName("deve adicionar bonus de fator_casa quando time e mandante")
        void deveAdicionarBonusFatorCasa() {
            var atleta = base().atletaId(1).clubeId(10).mediaPontos(0.0).valorizacao(0.0).build();
            var sem    = scoreService.calcularScores(List.of(atleta), Set.of(),    Set.of()).get(0).getScore();
            var com    = scoreService.calcularScores(List.of(atleta), Set.of(10), Set.of()).get(0).getScore();
            assertThat(com - sem).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("deve adicionar bonus de time_favorito quando clube e favorito")
        void deveAdicionarBonusTimeFavorito() {
            var atleta = base().nomeClubeNorm("flamengo").mediaPontos(0.0).valorizacao(0.0).build();
            var sem    = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of()).get(0).getScore();
            var com    = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of("flamengo")).get(0).getScore();
            assertThat(com - sem).isCloseTo(1.0, within(0.001));
        }

        @Test
        @DisplayName("deve acumular ambos os bonus simultaneamente")
        void deveAcumularAmbosOsBonus() {
            var atleta = base().atletaId(1).clubeId(5).nomeClubeNorm("flamengo")
                    .mediaPontos(0.0).valorizacao(0.0).build();
            var sem  = scoreService.calcularScores(List.of(atleta), Set.of(),    Set.of()).get(0).getScore();
            var com  = scoreService.calcularScores(List.of(atleta), Set.of(5), Set.of("flamengo")).get(0).getScore();
            assertThat(com - sem).isCloseTo(2.0, within(0.001));
        }

        @Test
        @DisplayName("deve preservar imutabilidade do atleta original")
        void devePreservarImutabilidade() {
            var atleta = base().score(99.0).build();
            scoreService.calcularScores(List.of(atleta), Set.of(), Set.of());
            assertThat(atleta.getScore()).isEqualTo(99.0);
        }

        @Test
        @DisplayName("deve processar lista vazia sem erros")
        void deveProcessarListaVazia() {
            assertThat(scoreService.calcularScores(List.of(), Set.of(), Set.of())).isEmpty();
        }

        @Test
        @DisplayName("deve manter todos os campos do atleta apos calculo")
        void deveMaterCamposDoAtleta() {
            var atleta = base().apelido("Hulk").posicao(Posicao.ATA).preco(18.5).build();
            var resultado = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of()).get(0);
            assertThat(resultado.getApelido()).isEqualTo("Hulk");
            assertThat(resultado.getPosicao()).isEqualTo(Posicao.ATA);
            assertThat(resultado.getPreco()).isEqualTo(18.5);
        }

        @ParameterizedTest(name = "media={0}, variacao={1} -> score aprox {2}")
        @CsvSource({ "10.0, 5.0, 7.0", "5.0, 0.0, 3.0", "0.0, 5.0, 1.0" })
        @DisplayName("deve calcular score ponderado corretamente")
        void deveCalcularScorePonderado(double media, double variacao, double esperado) {
            var atleta = base().mediaPontos(media).valorizacao(variacao).build();
            var resultado = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of());
            assertThat(resultado.get(0).getScore()).isCloseTo(esperado, within(0.001));
        }
    }

    @Nested
    @DisplayName("calcularScores — com mapa de desempenho real")
    class ComDesempenhoReal {

        @Test
        @DisplayName("deve usar desempenho real do mapa quando atletaId presente")
        void deveUsarDesempenhoReal() {
            // desempenho real = 8.0, mediaPontos = 4.0
            // score = 4.0*0.40 + 0*0.20 + 8.0*0.20 = 1.6 + 0 + 1.6 = 3.2
            var atleta = base().atletaId(42).mediaPontos(4.0).valorizacao(0.0).build();
            var desempenhoMap = Map.of(42, 8.0);

            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), desempenhoMap);

            assertThat(resultado.get(0).getScore()).isCloseTo(3.2, within(0.001));
        }

        @Test
        @DisplayName("deve usar mediaPontos como fallback quando atletaId ausente do mapa")
        void deveUsarFallbackQuandoIdAusenteDoMapa() {
            var atleta = base().atletaId(99).mediaPontos(10.0).valorizacao(0.0).build();
            var desempenhoMap = Map.of(1, 5.0);

            var semMapa = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of()).get(0).getScore();
            var comMapa = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of(), desempenhoMap).get(0).getScore();

            assertThat(comMapa).isCloseTo(semMapa, within(0.001));
        }

        @Test
        @DisplayName("deve preencher desempenhoRecente com valor real do mapa")
        void devePreencherDesempenhoRecente() {
            var atleta = base().atletaId(10).mediaPontos(5.0).build();
            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(10, 9.0));

            assertThat(resultado.get(0).getDesempenhoRecente()).isEqualTo(9.0);
        }

        @Test
        @DisplayName("deve gerar score diferente com desempenho real vs proxy")
        void deveGerarScoreDiferenteComDesempenhoReal() {
            var atleta = base().atletaId(5).mediaPontos(5.0).valorizacao(0.0).build();
            var comProxy = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of()).get(0).getScore();
            var comReal = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(5, 10.0)).get(0).getScore();

            assertThat(comReal).isGreaterThan(comProxy);
        }

        @Test
        @DisplayName("deve combinar desempenho real com bonus de casa e favorito")
        void deveCombinarComBonus() {
            var atleta = base().atletaId(7).clubeId(7).nomeClubeNorm("fla")
                    .mediaPontos(0.0).valorizacao(0.0).build();

            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(7), Set.of("fla"), Map.of(7, 6.0));

            // desempenho=6.0*0.20=1.2 + casa=10*0.10=1.0 + favorito=10*0.10=1.0 = 3.2
            assertThat(resultado.get(0).getScore()).isCloseTo(3.2, within(0.001));
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private Atleta.AtletaBuilder base() {
        return Atleta.builder()
                .atletaId(1)
                .apelido("Jogador")
                .posicao(Posicao.MEI)
                .clubeId(1)
                .nomeClube("Flamengo")
                .siglaClube("FLA")
                .nomeClubeNorm("flamengo")
                .status(StatusAtleta.PROVAVEL)
                .mediaPontos(5.0)
                .valorizacao(2.0)
                .preco(15.0)
                .desempenhoRecente(0.0)
                .score(0.0);
    }
}
