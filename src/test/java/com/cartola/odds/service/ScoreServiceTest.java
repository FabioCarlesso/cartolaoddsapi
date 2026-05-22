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
            var desempenhoMap = Map.of(42, desemp(8.0));

            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), desempenhoMap);

            assertThat(resultado.get(0).getScore()).isCloseTo(3.2, within(0.001));
        }

        @Test
        @DisplayName("deve usar mediaPontos como fallback quando atletaId ausente do mapa")
        void deveUsarFallbackQuandoIdAusenteDoMapa() {
            var atleta = base().atletaId(99).mediaPontos(10.0).valorizacao(0.0).build();
            var desempenhoMap = Map.of(1, desemp(5.0));

            var semMapa = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of()).get(0).getScore();
            var comMapa = scoreService.calcularScores(List.of(atleta), Set.of(), Set.of(), desempenhoMap).get(0).getScore();

            assertThat(comMapa).isCloseTo(semMapa, within(0.001));
        }

        @Test
        @DisplayName("deve preencher desempenhoRecente com valor real do mapa")
        void devePreencherDesempenhoRecente() {
            var atleta = base().atletaId(10).mediaPontos(5.0).build();
            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(10, desemp(9.0)));

            assertThat(resultado.get(0).getDesempenhoRecente()).isEqualTo(9.0);
        }

        @Test
        @DisplayName("deve gerar score diferente com desempenho real vs proxy")
        void deveGerarScoreDiferenteComDesempenhoReal() {
            var atleta = base().atletaId(5).mediaPontos(5.0).valorizacao(0.0).build();
            var comProxy = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of()).get(0).getScore();
            var comReal = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(5, desemp(10.0))).get(0).getScore();

            assertThat(comReal).isGreaterThan(comProxy);
        }

        @Test
        @DisplayName("deve combinar desempenho real com bonus de casa e favorito")
        void deveCombinarComBonus() {
            var atleta = base().atletaId(7).clubeId(7).nomeClubeNorm("fla")
                    .mediaPontos(0.0).valorizacao(0.0).build();

            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(7), Set.of("fla"), Map.of(7, desemp(6.0)));

            // desempenho=6.0*0.20=1.2 + casa=10*0.10=1.0 + favorito=10*0.10=1.0 = 3.2
            assertThat(resultado.get(0).getScore()).isCloseTo(3.2, within(0.001));
        }
    }

    @Nested
    @DisplayName("calcularScores — score específico por posição")
    class ScorePorPosicao {

        @Nested
        @DisplayName("Goleiro (GOL)")
        class Goleiro {

            @Test
            @DisplayName("deve usar formula especifica com maior peso em desempenho para goleiro")
            void deveUsarFormulaGoleiro() {
                // desempenho=8, mediaPontos=6, valorizacao=0, sem scouts, sem bonus situacional
                // score = 8*0.35 + 6*0.25 + 0*0.10 = 2.80 + 1.50 = 4.30
                var goleiro = base().posicao(Posicao.GOL).mediaPontos(6.0).valorizacao(0.0).atletaId(10).build();
                var resultado = scoreService.calcularScores(
                        List.of(goleiro), Set.of(), Set.of(), Map.of(10, desemp(8.0)));

                assertThat(resultado.get(0).getScore()).isCloseTo(4.30, within(0.001));
            }

            @Test
            @DisplayName("deve adicionar bonus por defesas difíceis (DD) no score do goleiro")
            void deveAdicionarBonusDefesasDificeis() {
                var semDD = base().posicao(Posicao.GOL).mediaPontos(0.0).valorizacao(0.0)
                        .defesasDificeis(0).build();
                var comDD = base().posicao(Posicao.GOL).mediaPontos(0.0).valorizacao(0.0)
                        .defesasDificeis(10).build();

                double scoreSem = scoreService.calcularScores(List.of(semDD), Set.of(), Set.of()).get(0).getScore();
                double scoreCom = scoreService.calcularScores(List.of(comDD), Set.of(), Set.of()).get(0).getScore();

                // delta = 10 * GOL_PESO_DEFESAS_DIFICEIS = 10 * 0.05 = 0.50
                assertThat(scoreCom - scoreSem).isCloseTo(10 * ScoreService.GOL_PESO_DEFESAS_DIFICEIS, within(0.001));
            }

            @Test
            @DisplayName("deve penalizar gols sofridos (GS) no score do goleiro")
            void devePenalizarGolsSofridos() {
                var semGS = base().posicao(Posicao.GOL).mediaPontos(0.0).valorizacao(0.0)
                        .golsSofridos(0).build();
                var comGS = base().posicao(Posicao.GOL).mediaPontos(0.0).valorizacao(0.0)
                        .golsSofridos(20).build();

                double scoreSem = scoreService.calcularScores(List.of(semGS), Set.of(), Set.of()).get(0).getScore();
                double scoreCom = scoreService.calcularScores(List.of(comGS), Set.of(), Set.of()).get(0).getScore();

                // delta = -(20 * GOL_PENALIZACAO_GOLS_SFD) = -(20 * 0.02) = -0.40
                assertThat(scoreSem - scoreCom).isCloseTo(20 * ScoreService.GOL_PENALIZACAO_GOLS_SFD, within(0.001));
            }

            @Test
            @DisplayName("deve adicionar bonus por penaltis defendidos (DP) no score do goleiro")
            void deveAdicionarBonusPenaltisDefendidos() {
                var semDP = base().posicao(Posicao.GOL).mediaPontos(0.0).valorizacao(0.0)
                        .penaltisDefendidos(0).build();
                var comDP = base().posicao(Posicao.GOL).mediaPontos(0.0).valorizacao(0.0)
                        .penaltisDefendidos(3).build();

                double scoreSem = scoreService.calcularScores(List.of(semDP), Set.of(), Set.of()).get(0).getScore();
                double scoreCom = scoreService.calcularScores(List.of(comDP), Set.of(), Set.of()).get(0).getScore();

                // delta = 3 * GOL_PESO_PENALTIS_DEF = 3 * 0.05 = 0.15
                assertThat(scoreCom - scoreSem).isCloseTo(3 * ScoreService.GOL_PESO_PENALTIS_DEF, within(0.001));
            }

            @Test
            @DisplayName("deve calcular score diferente da formula padrao para goleiro com mesmos dados")
            void deveCalcularScoreDiferenteDaPadrao() {
                var goleiro = base().posicao(Posicao.GOL).build();
                var meia    = base().posicao(Posicao.MEI).build();

                double scoreGol = scoreService.calcularScores(List.of(goleiro), Set.of(), Set.of()).get(0).getScore();
                double scoreMei = scoreService.calcularScores(List.of(meia),    Set.of(), Set.of()).get(0).getScore();

                assertThat(scoreGol).isNotEqualTo(scoreMei);
            }
        }

        @Nested
        @DisplayName("Atacante (ATA)")
        class Atacante {

            @Test
            @DisplayName("deve adicionar bonus por gols (G) no score do atacante")
            void deveAdicionarBonusGols() {
                var semGols = base().posicao(Posicao.ATA).mediaPontos(0.0).valorizacao(0.0)
                        .gols(0).build();
                var comGols = base().posicao(Posicao.ATA).mediaPontos(0.0).valorizacao(0.0)
                        .gols(15).build();

                double scoreSem = scoreService.calcularScores(List.of(semGols), Set.of(), Set.of()).get(0).getScore();
                double scoreCom = scoreService.calcularScores(List.of(comGols), Set.of(), Set.of()).get(0).getScore();

                // delta = 15 * ATA_PESO_GOLS = 15 * 0.08 = 1.20
                assertThat(scoreCom - scoreSem).isCloseTo(15 * ScoreService.ATA_PESO_GOLS, within(0.001));
            }

            @Test
            @DisplayName("deve adicionar bonus por assistencias (A) no score do atacante")
            void deveAdicionarBonusAssistencias() {
                var semA = base().posicao(Posicao.ATA).mediaPontos(0.0).valorizacao(0.0)
                        .assistencias(0).build();
                var comA = base().posicao(Posicao.ATA).mediaPontos(0.0).valorizacao(0.0)
                        .assistencias(8).build();

                double scoreSem = scoreService.calcularScores(List.of(semA), Set.of(), Set.of()).get(0).getScore();
                double scoreCom = scoreService.calcularScores(List.of(comA), Set.of(), Set.of()).get(0).getScore();

                // delta = 8 * ATA_PESO_ASSISTENCIAS = 8 * 0.05 = 0.40
                assertThat(scoreCom - scoreSem).isCloseTo(8 * ScoreService.ATA_PESO_ASSISTENCIAS, within(0.001));
            }

            @Test
            @DisplayName("deve calcular score diferente da formula padrao para atacante com mesmos dados")
            void deveCalcularScoreDiferenteDaPadrao() {
                var atacante = base().posicao(Posicao.ATA).build();
                var meia     = base().posicao(Posicao.MEI).build();

                double scoreAta = scoreService.calcularScores(List.of(atacante), Set.of(), Set.of()).get(0).getScore();
                double scoreMei = scoreService.calcularScores(List.of(meia),     Set.of(), Set.of()).get(0).getScore();

                assertThat(scoreAta).isNotEqualTo(scoreMei);
            }

            @Test
            @DisplayName("deve combinar gols, assistencias e time favorito no score do atacante")
            void deveCombinarScoutsETimeFavorito() {
                var atacante = base().posicao(Posicao.ATA).atletaId(99).clubeId(99)
                        .nomeClubeNorm("palmeiras").mediaPontos(0.0).valorizacao(0.0)
                        .gols(10).assistencias(5).build();

                var resultado = scoreService.calcularScores(
                        List.of(atacante), Set.of(), Set.of("palmeiras"));

                // gols=10*0.08=0.80 + assist=5*0.05=0.25 + timeFav=10*0.10=1.00 = 2.05
                assertThat(resultado.get(0).getScore()).isCloseTo(2.05, within(0.001));
            }
        }

        @Nested
        @DisplayName("Fallback para posições sem regra específica")
        class Fallback {

            @Test
            @DisplayName("deve usar formula padrao configuravel para MEI")
            void deveUsarFormulaPadraoParaMEI() {
                // media=10, desempenho=proxy=10: 10*0.40 + 0*0.20 + 10*0.20 = 4.0+2.0=6.0
                var meia = base().posicao(Posicao.MEI).mediaPontos(10.0).valorizacao(0.0).build();
                var resultado = scoreService.calcularScores(List.of(meia), Set.of(), Set.of());
                assertThat(resultado.get(0).getScore()).isCloseTo(6.0, within(0.001));
            }

            @Test
            @DisplayName("deve usar formula padrao configuravel para ZAG")
            void deveUsarFormulaPadraoParaZAG() {
                // media=10, desempenho=proxy=10: 10*0.40 + 0*0.20 + 10*0.20 = 6.0
                var zagueiro = base().posicao(Posicao.ZAG).mediaPontos(10.0).valorizacao(0.0).build();
                var resultado = scoreService.calcularScores(List.of(zagueiro), Set.of(), Set.of());
                assertThat(resultado.get(0).getScore()).isCloseTo(6.0, within(0.001));
            }
        }
    }

    @Nested
    @DisplayName("calcularScores — penalidade por desvio padrao")
    class PenalidadePorDesvio {

        @Test
        @DisplayName("atleta com mesma media e menor desvio deve receber score maior")
        void atletaMaisConsistenteRecebeScoreMaior() {
            var constante = base().atletaId(1).mediaPontos(0.0).valorizacao(0.0).build();
            var instavel  = base().atletaId(2).mediaPontos(0.0).valorizacao(0.0).build();

            var scoreConstante = scoreService.calcularScores(
                    List.of(constante), Set.of(), Set.of(), Map.of(1, desemp(7.0, 0.0))).get(0).getScore();
            var scoreInstavel = scoreService.calcularScores(
                    List.of(instavel), Set.of(), Set.of(), Map.of(2, desemp(7.0, 4.0))).get(0).getScore();

            assertThat(scoreConstante).isGreaterThan(scoreInstavel);
        }

        @Test
        @DisplayName("pesoDesvio = 0.0 deve manter o score neutro (sem penalidade)")
        void pesoDesvioZeroNaoAlteraScore() {
            when(configuracaoService.buscarConfig()).thenReturn(configComPesoDesvio(0.0));

            var atleta = base().atletaId(1).mediaPontos(0.0).valorizacao(0.0).build();

            var semDesvio = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(1, desemp(7.0, 0.0))).get(0).getScore();
            var comDesvio = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(1, desemp(7.0, 4.0))).get(0).getScore();

            assertThat(comDesvio).isCloseTo(semDesvio, within(0.001));
        }

        @Test
        @DisplayName("pesoDesvio = 0.05 com desvio = 4.0 deve aplicar penalidade de 0.20")
        void deveAplicarPenalidadeDe020() {
            // fallback: media=10*0.40 + 0*0.20 + desempenho=10*0.20 = 6.0 ; penalidade = 4.0*0.05 = 0.20
            var atleta = base().atletaId(1).mediaPontos(10.0).valorizacao(0.0).build();

            var semDesvio = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(1, desemp(10.0, 0.0))).get(0).getScore();
            var comDesvio = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(1, desemp(10.0, 4.0))).get(0).getScore();

            assertThat(semDesvio).isCloseTo(6.0, within(0.001));
            assertThat(comDesvio).isCloseTo(5.80, within(0.001));
            assertThat(semDesvio - comDesvio).isCloseTo(0.20, within(0.001));
        }

        @Test
        @DisplayName("goleiro com desvio deve ser penalizado pela formula especifica")
        void devePenalizarGoleiro() {
            var goleiro = base().posicao(Posicao.GOL).atletaId(1).mediaPontos(6.0).valorizacao(0.0).build();

            var semDesvio = scoreService.calcularScores(
                    List.of(goleiro), Set.of(), Set.of(), Map.of(1, desemp(8.0, 0.0))).get(0).getScore();
            var comDesvio = scoreService.calcularScores(
                    List.of(goleiro), Set.of(), Set.of(), Map.of(1, desemp(8.0, 4.0))).get(0).getScore();

            // penalidade = 4.0 * 0.05 = 0.20
            assertThat(semDesvio - comDesvio).isCloseTo(0.20, within(0.001));
        }

        @Test
        @DisplayName("atacante com desvio deve ser penalizado pela formula especifica")
        void devePenalizarAtacante() {
            var atacante = base().posicao(Posicao.ATA).atletaId(1).mediaPontos(6.0).valorizacao(0.0).build();

            var semDesvio = scoreService.calcularScores(
                    List.of(atacante), Set.of(), Set.of(), Map.of(1, desemp(8.0, 0.0))).get(0).getScore();
            var comDesvio = scoreService.calcularScores(
                    List.of(atacante), Set.of(), Set.of(), Map.of(1, desemp(8.0, 4.0))).get(0).getScore();

            // penalidade = 4.0 * 0.05 = 0.20
            assertThat(semDesvio - comDesvio).isCloseTo(0.20, within(0.001));
        }

        @Test
        @DisplayName("nao deve penalizar quando atleta usa proxy (ausente do mapa)")
        void naoPenalizaQuandoUsaProxy() {
            var atleta = base().atletaId(1).mediaPontos(10.0).valorizacao(0.0).build();

            // mapa nao contem o atleta -> usa media da temporada como proxy, sem desvio associado
            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(2, desemp(5.0, 9.0)));

            // score = 10*0.40 + 0 + 10*0.20 = 6.0, sem qualquer penalidade
            assertThat(resultado.get(0).getScore()).isCloseTo(6.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("calcularScores — exposicao de desvioPadrao e rodadasConsideradas")
    class CamposDeDesempenhoExpostos {

        @Test
        @DisplayName("deve preencher desvioPadrao e rodadasConsideradas com o historico real")
        void devePreencherCamposComHistoricoReal() {
            var atleta = base().atletaId(10).mediaPontos(5.0).build();
            var desempenho = new DesempenhoService.DesempenhoAtleta(8.0, 2.5, 4);

            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(10, desempenho)).get(0);

            assertThat(resultado.getDesvioPadrao()).isCloseTo(2.5, within(0.001));
            assertThat(resultado.getRodadasConsideradas()).isEqualTo(4);
        }

        @Test
        @DisplayName("deve zerar desvioPadrao e rodadasConsideradas quando atleta usa proxy")
        void deveZerarCamposComProxy() {
            var atleta = base().atletaId(99).mediaPontos(10.0).build();

            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(1, desemp(5.0, 9.0))).get(0);

            assertThat(resultado.getDesvioPadrao()).isEqualTo(0.0);
            assertThat(resultado.getRodadasConsideradas()).isEqualTo(0);
        }

        @Test
        @DisplayName("deve arredondar desvioPadrao para 4 casas decimais")
        void deveArredondarDesvioPadrao() {
            var atleta = base().atletaId(7).mediaPontos(5.0).build();
            // desvio com muitas casas: 1.2345678 -> 1.2346
            var desempenho = new DesempenhoService.DesempenhoAtleta(8.0, 1.2345678, 5);

            var resultado = scoreService.calcularScores(
                    List.of(atleta), Set.of(), Set.of(), Map.of(7, desempenho)).get(0);

            assertThat(resultado.getDesvioPadrao()).isEqualTo(1.2346);
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private DesempenhoService.DesempenhoAtleta desemp(double media) {
        return desemp(media, 0.0);
    }

    private DesempenhoService.DesempenhoAtleta desemp(double media, double desvio) {
        return new DesempenhoService.DesempenhoAtleta(media, desvio, DesempenhoService.RODADAS_HISTORICO);
    }

    private Configuracao configComPesoDesvio(double pesoDesvio) {
        var config = Configuracao.defaults();
        config.setPesoDesvio(pesoDesvio);
        return config;
    }

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
                .defesasDificeis(0)
                .golsSofridos(0)
                .penaltisDefendidos(0)
                .gols(0)
                .assistencias(0)
                .desempenhoRecente(0.0)
                .score(0.0);
    }
}
