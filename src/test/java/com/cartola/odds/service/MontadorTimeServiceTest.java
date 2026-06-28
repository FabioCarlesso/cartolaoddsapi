package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.FormacaoConfig;
import com.cartola.odds.model.enums.Estrategia;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.util.FormacaoParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MontadorTimeService")
class MontadorTimeServiceTest {

    @Mock ConfiguracaoService configuracaoService;

    private MontadorTimeService service;
    private Configuracao config;

    @BeforeEach
    void setUp() {
        config = Configuracao.defaults();
        when(configuracaoService.buscarConfig()).thenReturn(config);
        service = new MontadorTimeService(configuracaoService);
    }

    @Nested
    @DisplayName("formacao 4-3-3")
    class Formacao {

        @Test
        @DisplayName("deve montar exatamente 1 GOL, 2 LAT, 2 ZAG, 3 MEI, 3 ATA, 1 TEC")
        void deveMontarFormacaoCompleta() {
            var time = service.montar(criarPool(), 10, null);

            assertThat(time.getTitulares().get(Posicao.GOL)).hasSize(1);
            assertThat(time.getTitulares().get(Posicao.LAT)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.MEI)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.ATA)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.TEC)).hasSize(1);
        }

        @Test
        @DisplayName("deve registrar a rodada corretamente no Time")
        void deveRegistrarRodada() {
            var time = service.montar(criarPool(), 22, null);
            assertThat(time.getRodada()).isEqualTo(22);
        }

        @Test
        @DisplayName("deve selecionar titulares com maior score em cada posicao")
        void deveEscolherMaioresScoresPorPosicao() {
            var time = service.montar(criarPool(), 1, null);

            time.getTitulares().forEach((pos, titulares) -> {
                var reserva = time.getReservas().get(pos);
                if (reserva != null) {
                    double minScoreTitular = titulares.stream()
                            .mapToDouble(Atleta::getScore).min().orElse(0);
                    assertThat(minScoreTitular).isGreaterThanOrEqualTo(reserva.getScore());
                }
            });
        }
    }

    @Nested
    @DisplayName("regra de defesa sem clube repetido")
    class RegraDefesaSemClubeRepetido {

        @Test
        @DisplayName("quando ativa nao deve repetir clube entre GOL, LAT e ZAG titulares")
        void quandoAtivaNaoDeveRepetirClubeNaDefesa() {
            var time = service.montar(poolComDefensoresRepetidos(), 1, null);

            var clubesDefesa = titularesDefesa(time);

            assertThat(new HashSet<>(clubesDefesa)).hasSameSizeAs(clubesDefesa);
        }

        @Test
        @DisplayName("quando inativa permite repetir clube entre GOL, LAT e ZAG titulares")
        void quandoInativaPermiteRepetirClubeNaDefesa() {
            config.setEvitarMesmoClubeDefesa(false);

            var time = service.montar(poolComDefensoresRepetidos(), 1, null);

            assertThat(Collections.frequency(titularesDefesa(time), 10)).isEqualTo(3);
        }

        @Test
        @DisplayName("quando candidatos insuficientes a regra completa o time sem falhar")
        void quandoClubesInsuficientesTimeDeveEstarCompleto() {
            var time = service.montar(poolComDefesaUmSoClube(), 1, null);

            assertThat(time.getTitulares().get(Posicao.GOL)).hasSize(1);
            assertThat(time.getTitulares().get(Posicao.LAT)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(2);
        }

        @Test
        @DisplayName("regra nao limita MEI, ATA e TEC")
        void regraNaoLimitaPosicoesOfensivas() {
            var time = service.montar(poolComDefensoresRepetidos(), 1, null);

            assertThat(time.getTitulares().get(Posicao.MEI))
                    .extracting(Atleta::getClubeId)
                    .containsOnly(200);
            assertThat(time.getTitulares().get(Posicao.ATA))
                    .extracting(Atleta::getClubeId)
                    .containsOnly(300);
            assertThat(time.getTitulares().get(Posicao.TEC))
                    .extracting(Atleta::getClubeId)
                    .containsOnly(400);
        }
    }

    @Nested
    @DisplayName("capitao")
    class Capitao {

        @Test
        @DisplayName("deve eleger atleta com maior score como capitao")
        void deveElegerCapitaoComMaiorScore() {
            var time = service.montar(criarPool(), 1, null);

            double maxScore = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .mapToDouble(Atleta::getScore)
                    .max().orElse(0);

            assertThat(time.getCapitao()).isNotNull();
            assertThat(time.getCapitao().getScore()).isEqualTo(maxScore);
        }

        @Test
        @DisplayName("deve preferir ATA como capitao quando tem maior score")
        void devePreferirAtaComoCapitao() {
            var pool = criarPool();
            pool.add(atletaBuilder(Posicao.ATA, 100, 999.0, 50.0, 100).build());

            var time = service.montar(pool, 1, null);

            assertThat(time.getCapitao().getPosicao()).isEqualTo(Posicao.ATA);
        }

        @Test
        @DisplayName("reserva de luxo deve ser o melhor score entre as reservas")
        void reservaLuxoDeveSerMelhorReserva() {
            var time = service.montar(criarPool(), 1, null);

            assertThat(time.getReservaLuxo()).isNotNull();
            double maiorScoreReserva = time.getReservas().values().stream()
                    .mapToDouble(Atleta::getScore)
                    .max()
                    .orElseThrow();
            assertThat(time.getReservaLuxo().getScore()).isEqualTo(maiorScoreReserva);
        }

        @Test
        @DisplayName("reserva de luxo deve pertencer ao conjunto de reservas e nao aos titulares")
        void reservaLuxoDevePertencerAoConjuntoDeReservas() {
            var time = service.montar(criarPool(), 1, null);

            Set<String> apelidosReservas = time.getReservas().values().stream()
                    .map(Atleta::getApelido)
                    .collect(Collectors.toSet());
            Set<String> apelidosTitulares = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .map(Atleta::getApelido)
                    .collect(Collectors.toSet());
            assertThat(time.getReservaLuxo().getApelido()).isIn(apelidosReservas);
            assertThat(time.getReservaLuxo().getApelido()).isNotIn(apelidosTitulares);
        }
    }

    @Nested
    @DisplayName("limite maximo por clube")
    class LimiteMaximoPorClube {

        @Test
        @DisplayName("deve escalar no maximo 4 atletas do mesmo clube nos titulares incluindo TEC")
        void deveRespeitarLimiteMaximoPorClube() {
            var time = service.montar(poolComMuitosAtletasMesmoClube(), 1, null);

            var maiorQtdPorClube = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.groupingBy(Atleta::getClubeId, Collectors.counting()))
                    .values().stream()
                    .max(Long::compareTo)
                    .orElse(0L);

            assertThat(maiorQtdPorClube).isLessThanOrEqualTo(4L);
            assertThat(time.getTitulares().get(Posicao.GOL)).hasSize(1);
            assertThat(time.getTitulares().get(Posicao.LAT)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.MEI)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.ATA)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.TEC)).hasSize(1);
        }

        @Test
        @DisplayName("deve respeitar limite configuravel por clube")
        void deveRespeitarLimiteConfiguravelPorClube() {
            config.setLimiteAtletasPorClube(2);

            var time = service.montar(poolComMuitosAtletasMesmoClube(), 1, null);

            var maiorQtdPorClube = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.groupingBy(Atleta::getClubeId, Collectors.counting()))
                    .values().stream()
                    .max(Long::compareTo)
                    .orElse(0L);

            assertThat(maiorQtdPorClube).isLessThanOrEqualTo(2L);
        }

        @Test
        @DisplayName("fallback intermediario relaxa regra de defesa mas rejeita clube que atingiu o limite")
        void fallbackIntermediarioRespeitaLimitePorClubeAoRelaxarDefesa() {
            // Com limite=2: GOL(clube10) + LAT(clube10 via fallback intermediario) = 2 → clube10 satura
            // ZAG tem candidatos do clube10 (no limite) e clube11 (na defesa, mas abaixo do limite)
            // Esperado: ZAG recebe clube12 via primary + clube11 via fallback intermediario
            //           clube10 rejeitado no fallback intermediario por atingir o limite
            config.setLimiteAtletasPorClube(2);

            List<Atleta> pool = new ArrayList<>();

            // GOL: clube10 selecionado → defesa={10}, contagem={10:1}
            pool.add(atletaBuilder(Posicao.GOL, 1, 50.0, 20.0, 10).build());
            pool.add(atletaBuilder(Posicao.GOL, 2,  5.0, 10.0, 99).build());

            // LAT: primary seleciona clube11 (passa ambas as regras)
            //      fallback intermediario seleciona clube10 (defesa bloqueada, limite ok → contagem{10:2})
            pool.add(atletaBuilder(Posicao.LAT, 3, 49.0, 20.0, 10).build()); // bloqueado por defesa no primary
            pool.add(atletaBuilder(Posicao.LAT, 4, 48.0, 20.0, 11).build()); // passa primary
            pool.add(atletaBuilder(Posicao.LAT, 5, 47.0, 20.0, 10).build()); // extra do clube10

            // ZAG: clube10 no limite → rejeitado mesmo no fallback intermediario
            //      clube11 na defesa, mas abaixo do limite → selecionado via fallback intermediario
            //      clube12 passa primary diretamente
            pool.add(atletaBuilder(Posicao.ZAG, 6, 46.0, 20.0, 10).build()); // no limite → rejeitado
            pool.add(atletaBuilder(Posicao.ZAG, 7, 45.0, 20.0, 10).build()); // no limite → rejeitado
            pool.add(atletaBuilder(Posicao.ZAG, 8, 44.0, 20.0, 11).build()); // na defesa, nao no limite → via fallback
            pool.add(atletaBuilder(Posicao.ZAG, 9, 43.0, 20.0, 12).build()); // passa primary

            pool.addAll(criarAtletasMesmoClube(Posicao.MEI, 5, 20, 200));
            pool.addAll(criarAtletasMesmoClube(Posicao.ATA, 5, 30, 300));
            pool.addAll(criarAtletasMesmoClube(Posicao.TEC, 2, 40, 400));

            var time = service.montar(pool, 1, null);

            // Formacao completa — fallback intermediario supriu as vagas de ZAG
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(2);

            // Clube10 nao deve estar no ZAG (foi rejeitado por atingir o limite)
            Set<Integer> zagClubes = time.getTitulares().get(Posicao.ZAG).stream()
                    .map(Atleta::getClubeId)
                    .collect(Collectors.toSet());
            assertThat(zagClubes).doesNotContain(10);

            // Clube10 deve ter exatamente 2 atletas no time (GOL + LAT via fallback intermediario)
            long qtdClub10 = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .filter(a -> a.getClubeId() == 10)
                    .count();
            assertThat(qtdClub10).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("reservas")
    class Reservas {

        @Test
        @DisplayName("reserva deve ser da mesma posicao do titular")
        void reservaDeveSerDaMesmaPosicao() {
            var time = service.montar(criarPool(), 1, null);

            time.getReservas().forEach((posicao, reserva) ->
                    assertThat(reserva.getPosicao()).isEqualTo(posicao));
        }

        @Test
        @DisplayName("tecnico nao deve ter reserva")
        void tecnicoNaoDeveTerReserva() {
            var time = service.montar(criarPool(), 1, null);

            assertThat(time.getTitulares().get(Posicao.TEC)).hasSize(1);
            assertThat(time.getReservas()).doesNotContainKey(Posicao.TEC);
        }

        @Test
        @DisplayName("reserva deve ter status PROVAVEL")
        void reservaDeveSerProvavel() {
            var time = service.montar(criarPool(), 1, null);

            time.getReservas().values().forEach(reserva ->
                    assertThat(reserva.getStatus()).isEqualTo(StatusAtleta.PROVAVEL));
        }

        @Test
        @DisplayName("reserva nao deve ser o mesmo atleta do titular")
        void reservaNaoDeveSerTitular() {
            var time = service.montar(criarPool(), 1, null);

            var apelidosTitulares = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .map(Atleta::getApelido)
                    .toList();

            time.getReservas().values().forEach(reserva ->
                    assertThat(apelidosTitulares).doesNotContain(reserva.getApelido()));
        }

        @Test
        @DisplayName("reserva deve ser mais barata que o titular mais caro quando possivel")
        void reservaDeveSerMaisBarata() {
            var time = service.montar(criarPool(), 1, null);

            time.getReservas().forEach((pos, reserva) -> {
                double maxPrecoTitular = time.getTitulares().get(pos).stream()
                        .mapToDouble(Atleta::getPreco)
                        .max().orElse(Double.MAX_VALUE);
                assertThat(reserva.getPreco()).isLessThanOrEqualTo(maxPrecoTitular + 0.01);
            });
        }
    }

    @Nested
    @DisplayName("titulares em duvida")
    class TitularesEmDuvida {

        @Test
        @DisplayName("deve mapear substituto provavel para titular em duvida")
        void deveMapeiarSubstitutoParaDuvida() {
            var time = service.montar(poolComAtaEmDuvida(), 1, null);

            var emDuvida = time.getTitulares().get(Posicao.ATA).stream()
                    .filter(Atleta::isDuvida)
                    .findFirst();

            assertThat(emDuvida).isPresent();
            assertThat(emDuvida.get().getSubstitutoProvavel()).isNotNull();
        }

        @Test
        @DisplayName("substituto deve ser da MESMA posicao individual do titular em duvida")
        void substitutoDeveSerDaMesmaPosicao() {
            var time = service.montar(poolComAtaEmDuvida(), 1, null);

            time.getTitulares().get(Posicao.ATA).stream()
                    .filter(Atleta::isDuvida)
                    .forEach(j -> {
                        assertThat(j.getSubstitutoProvavel()).isNotNull();
                        assertThat(j.getSubstitutoProvavel().getPosicao()).isEqualTo(Posicao.ATA);
                    });
        }

        @Test
        @DisplayName("substituto nao deve ser outro titular ja escalado")
        void substitutoNaoDeveSerTitular() {
            var time = service.montar(poolComAtaEmDuvida(), 1, null);

            var apelidosTitulares = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .map(Atleta::getApelido)
                    .toList();

            time.getTitulares().get(Posicao.ATA).stream()
                    .filter(Atleta::isDuvida)
                    .filter(j -> j.getSubstitutoProvavel() != null)
                    .forEach(j -> assertThat(apelidosTitulares)
                            .doesNotContain(j.getSubstitutoProvavel().getApelido()));
        }

        @Test
        @DisplayName("deve gerar alerta para cada titular em duvida")
        void deveGerarAlertaParaDuvida() {
            var time = service.montar(poolComAtaEmDuvida(), 1, null);

            assertThat(time.getAlertasDuvida()).isNotEmpty();
        }

        @Test
        @DisplayName("titular provavel nao deve ter substituto preenchido")
        void titularProvavelNaoDeveTermSubstituto() {
            var time = service.montar(criarPool(), 1, null);

            time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .filter(Atleta::isProvavel)
                    .forEach(j -> assertThat(j.getSubstitutoProvavel()).isNull());
        }
    }

    @Nested
    @DisplayName("custo total")
    class CustoTotal {

        @Test
        @DisplayName("deve calcular custo total somando preco de todos os titulares")
        void deveCalcularCustoTotal() {
            var time = service.montar(criarPool(), 1, null);

            double esperado = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .mapToDouble(Atleta::getPreco)
                    .sum();

            assertThat(time.getCustoTotal()).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("constraint de budget maximo")
    class BudgetMaximo {

        @Test
        @DisplayName("deve excluir jogadores caros quando budget e insuficiente para o preco deles")
        void deveSelecionarJogadoresDentroDoOrcamento() {
            config.setBudgetMaximo(24.0); // 12 titulares × C$2.0 = C$24.0

            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null);

            assertThat(time.getTitulares().get(Posicao.GOL)).hasSize(1);
            assertThat(time.getTitulares().get(Posicao.LAT)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.MEI)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.ATA)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.TEC)).hasSize(1);
            time.getTitulares().values().stream().flatMap(List::stream)
                    .forEach(a -> assertThat(a.getPreco()).isLessThan(50.0));
        }

        @Test
        @DisplayName("custo total dos titulares nao deve ultrapassar o budget maximo")
        void custoTotalNaoDeveUltrapassarBudgetMaximo() {
            config.setBudgetMaximo(24.0);

            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null);

            assertThat(time.getCustoTotal()).isLessThanOrEqualTo(24.0);
        }

        @Test
        @DisplayName("deve reservar budget para completar as posicoes restantes")
        void deveReservarBudgetParaCompletarPosicoesRestantes() {
            config.setBudgetMaximo(24.0); // 12 titulares × C$2.0 = C$24.0

            var time = service.montar(criarPoolPrecosMistos(10.0, 2.0), 1, null);

            assertThat(time.getTitulares().get(Posicao.GOL))
                    .singleElement()
                    .extracting(Atleta::getPreco)
                    .isEqualTo(2.0);
            assertThat(time.getTitulares().values().stream().mapToLong(List::size).sum()).isEqualTo(12L);
            assertThat(time.getCustoTotal()).isEqualTo(24.0);
        }

        @Test
        @DisplayName("quando budget e zero nao aplica constraint de custo")
        void quandoBudgetZeroNaoAplicaConstraint() {
            config.setBudgetMaximo(0.0);

            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null);

            // Sem constraint, todos os titulares devem ser os mais caros (maior score)
            time.getTitulares().values().stream().flatMap(List::stream)
                    .forEach(a -> assertThat(a.getPreco()).isEqualTo(50.0));
        }

        @Test
        @DisplayName("quando budget insuficiente para qualquer jogador nao lanca excecao")
        void quandoBudgetInsuficienteNaoLancaExcecao() {
            config.setBudgetMaximo(1.0); // menor que qualquer preco disponivel

            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null);

            assertThat(time).isNotNull();
            assertThat(time.getCustoTotal()).isLessThanOrEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("orcamento e custo-beneficio")
    class Orcamento {

        @Test
        @DisplayName("sem orcamento usa estrategia SCORE_MAXIMO e nao expoe orcamento/saldo")
        void semOrcamentoEstrategiaScoreMaximo() {
            var time = service.montar(criarPool(), 1, null, null);

            assertThat(time.getEstrategia()).isEqualTo(Estrategia.SCORE_MAXIMO);
            assertThat(time.getOrcamentoInformado()).isNull();
            assertThat(time.getSaldoRestante()).isNull();
            assertThat(time.isFormacaoCompleta()).isTrue();
            assertThat(time.getAvisoOrcamento()).isNull();
        }

        @Test
        @DisplayName("com orcamento usa estrategia SCORE_MAXIMO, expoe saldo e marca formacao completa")
        void comOrcamentoEstrategiaScoreMaximo() {
            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null, 300.0);

            assertThat(time.getEstrategia()).isEqualTo(Estrategia.SCORE_MAXIMO);
            assertThat(time.getOrcamentoInformado()).isEqualTo(300.0);
            assertThat(time.getSaldoRestante()).isEqualTo(300.0 - time.getCustoTotal());
            assertThat(time.isFormacaoCompleta()).isTrue();
            assertThat(time.getAvisoOrcamento()).isNull();
        }

        @Test
        @DisplayName("custo total nao deve ultrapassar o orcamento informado")
        void custoTotalNaoUltrapassaOrcamento() {
            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null, 24.0);

            assertThat(time.getCustoTotal()).isLessThanOrEqualTo(24.0);
            assertThat(time.getSaldoRestante()).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("orcamento insuficiente nao estoura o limite, nao lanca excecao e sinaliza formacao incompleta")
        void orcamentoInsuficienteSinalizaFormacaoIncompleta() {
            // Atleta mais barato custa C$2.0; com orcamento C$1.0 ninguem cabe.
            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null, 1.0);

            assertThat(time).isNotNull();
            assertThat(time.getCustoTotal()).isLessThanOrEqualTo(1.0);
            assertThat(time.getSaldoRestante()).isGreaterThanOrEqualTo(0.0);
            assertThat(time.isFormacaoCompleta()).isFalse();
            assertThat(time.getAvisoOrcamento()).isNotNull().contains("insuficiente");
        }

        @Test
        @DisplayName("orcamento alto reproduz o time de maior score por posicao (otimo absoluto)")
        void orcamentoAltoReproduzTimeOtimoAbsoluto() {
            // Os caros (preco 50) tem os maiores scores em cada posicao. Com orcamento
            // folgado o otimizador deve escolher exatamente esses, igual ao sem orcamento.
            var comOrcamento = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null, 10_000.0);
            var semOrcamento = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null, null);

            comOrcamento.getTitulares().values().stream().flatMap(List::stream)
                    .forEach(a -> assertThat(a.getPreco()).isEqualTo(50.0));

            assertThat(apelidosTitulares(comOrcamento))
                    .isEqualTo(apelidosTitulares(semOrcamento));
        }

        @Test
        @DisplayName("orcamento medio escolhe a combinacao de maior soma de score que cabe no teto")
        void orcamentoMedioMaximizaSomaDeScore() {
            // Posicoes fixas custam 9.0 (9 atletas a C$1.0). Sobram C$22.0 para 3 MEI:
            // 3 premium (score 10 / preco 10) custariam 30 (nao cabe); 2 premium + 1 cheap
            // custam 21 (cabe, score 21) e e a melhor combinacao dentro do teto.
            var time = service.montar(poolKnapsackMeio(), 1, null, 31.0);

            var precoMei = time.getTitulares().get(Posicao.MEI).stream()
                    .map(Atleta::getPreco)
                    .sorted()
                    .toList();
            assertThat(precoMei).containsExactly(1.0, 10.0, 10.0);
            assertThat(time.getCustoTotal()).isEqualTo(30.0);
            assertThat(time.isFormacaoCompleta()).isTrue();
        }

        @Test
        @DisplayName("em empate de score escolhe a solucao de menor custo (desempate por score/preco)")
        void empateDeScoreEscolheMenorCusto() {
            var time = service.montar(poolGolEmpatado(), 1, null, 200.0);

            assertThat(time.getTitulares().get(Posicao.GOL))
                    .singleElement()
                    .extracting(Atleta::getPreco)
                    .isEqualTo(3.0);
        }

        @Test
        @DisplayName("orcamento muito baixo preenche o maximo possivel e sinaliza aviso e incompleto")
        void orcamentoMuitoBaixoBestEffort() {
            // Cada atleta custa C$10.0; com C$25.0 cabem no maximo 2 titulares.
            var time = service.montar(criarPoolPreco(10.0), 1, null, 25.0);

            long escalados = time.getTitulares().values().stream().mapToLong(List::size).sum();
            assertThat(escalados).isGreaterThan(0).isLessThan(12);
            assertThat(time.getCustoTotal()).isLessThanOrEqualTo(25.0);
            assertThat(time.getSaldoRestante()).isGreaterThanOrEqualTo(0.0);
            assertThat(time.isFormacaoCompleta()).isFalse();
            assertThat(time.getAvisoOrcamento()).isNotNull().contains("insuficiente");
        }

        @Test
        @DisplayName("regra de defesa sem clube repetido continua respeitada sob orcamento")
        void regraDefesaRespeitadaSobOrcamento() {
            var time = service.montar(poolComDefensoresRepetidos(), 1, null, 200.0);

            var clubesDefesa = titularesDefesa(time);
            assertThat(new HashSet<>(clubesDefesa)).hasSameSizeAs(clubesDefesa);
        }

        @Test
        @DisplayName("limite por clube continua respeitado sob orcamento")
        void limitePorClubeRespeitadoSobOrcamento() {
            var time = service.montar(poolComMuitosAtletasMesmoClube(), 1, null, 500.0);

            var maiorQtdPorClube = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.groupingBy(Atleta::getClubeId, Collectors.counting()))
                    .values().stream()
                    .max(Long::compareTo)
                    .orElse(0L);

            assertThat(maiorQtdPorClube).isLessThanOrEqualTo(4L);
        }

        private Set<String> apelidosTitulares(com.cartola.odds.model.Time time) {
            return time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .map(Atleta::getApelido)
                    .collect(Collectors.toSet());
        }

        @Test
        @DisplayName("orcamento informado tem prioridade sobre budgetMaximo da config")
        void orcamentoTemPrioridadeSobreConfig() {
            config.setBudgetMaximo(1.0); // config restritiva seria insuficiente

            var time = service.montar(criarPoolPrecosMistos(50.0, 2.0), 1, null, 300.0);

            // orcamento=300 deve permitir a formacao completa apesar do budgetMaximo=1.0
            assertThat(time.getTitulares().values().stream().mapToLong(List::size).sum())
                    .isEqualTo(12L);
        }
    }

    @Nested
    @DisplayName("override de formacao")
    class OverrideFormacao {

        @Test
        @DisplayName("deve montar DEF=3 (LAT 2 + ZAG 1), 4 MEI e 3 ATA quando override e 3-4-3")
        void deveMontarFormacao343() {
            var time = service.montar(criarPool(), 1, null, null, new FormacaoConfig(3, 4, 3));

            assertThat(time.getTitulares().get(Posicao.GOL)).hasSize(1);
            assertThat(time.getTitulares().get(Posicao.LAT)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(1);
            assertThat(time.getTitulares().get(Posicao.MEI)).hasSize(4);
            assertThat(time.getTitulares().get(Posicao.ATA)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.TEC)).hasSize(1);
        }

        @Test
        @DisplayName("deve montar DEF=4 (LAT 2 + ZAG 2), 4 MEI e 2 ATA quando override e 4-4-2")
        void deveMontarFormacao442() {
            var time = service.montar(criarPool(), 1, null, null, new FormacaoConfig(4, 4, 2));

            assertThat(time.getTitulares().get(Posicao.LAT)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.MEI)).hasSize(4);
            assertThat(time.getTitulares().get(Posicao.ATA)).hasSize(2);
        }

        @Test
        @DisplayName("nao deve alterar a formacao da configuracao apos a montagem com override")
        void naoDeveAlterarConfiguracao() {
            service.montar(criarPool(), 1, null, null, new FormacaoConfig(3, 4, 3));

            assertThat(config.getFormacaoZag()).isEqualTo(2);
            assertThat(config.getFormacaoMei()).isEqualTo(3);
            assertThat(config.getFormacaoAta()).isEqualTo(3);
        }

        @Test
        @DisplayName("deve respeitar o limite por clube mesmo com override de formacao")
        void deveRespeitarLimitePorClubeComOverride() {
            var time = service.montar(poolComMuitosAtletasMesmoClube(), 1, null, null,
                    new FormacaoConfig(4, 4, 2));

            var maiorQtdPorClube = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.groupingBy(Atleta::getClubeId, Collectors.counting()))
                    .values().stream()
                    .max(Long::compareTo)
                    .orElse(0L);

            assertThat(maiorQtdPorClube).isLessThanOrEqualTo(4L);
        }

        @ParameterizedTest(name = "{0} -> GOL 1, LAT 2, ZAG {1}, MEI {2}, ATA {3}, TEC 1 (12 titulares)")
        @DisplayName("toda formacao suportada deve gerar 11 em campo + 1 TEC com a distribuicao correta")
        @CsvSource({
                "4-3-3, 2, 3, 3",
                "3-4-3, 1, 4, 3",
                "4-4-2, 2, 4, 2",
                "5-3-2, 3, 3, 2",
                "4-5-1, 2, 5, 1",
                "3-5-2, 1, 5, 2"
        })
        void deveMontarTodasFormacoesSuportadas(String formacao, int zag, int mei, int ata) {
            var override = FormacaoParser.parse(formacao);

            var time = service.montar(criarPool(), 1, null, null, override);

            assertThat(time.getTitulares().get(Posicao.GOL)).hasSize(1);
            assertThat(time.getTitulares().get(Posicao.LAT)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(zag);
            assertThat(time.getTitulares().get(Posicao.MEI)).hasSize(mei);
            assertThat(time.getTitulares().get(Posicao.ATA)).hasSize(ata);
            assertThat(time.getTitulares().get(Posicao.TEC)).hasSize(1);

            long totalTitulares = time.getTitulares().values().stream()
                    .mapToLong(List::size)
                    .sum();
            assertThat(totalTitulares).isEqualTo(12L);
        }

        @Test
        @DisplayName("override null mantem a formacao padrao da configuracao")
        void overrideNullMantemPadrao() {
            var time = service.montar(criarPool(), 1, null, null, null);

            assertThat(time.getTitulares().get(Posicao.ZAG)).hasSize(2);
            assertThat(time.getTitulares().get(Posicao.MEI)).hasSize(3);
            assertThat(time.getTitulares().get(Posicao.ATA)).hasSize(3);
        }
    }

    private List<Integer> titularesDefesa(com.cartola.odds.model.Time time) {
        return List.of(Posicao.GOL, Posicao.LAT, Posicao.ZAG).stream()
                .flatMap(posicao -> time.getTitulares().getOrDefault(posicao, List.of()).stream())
                .map(Atleta::getClubeId)
                .toList();
    }

    private List<Atleta> criarPool() {
        List<Atleta> pool = new ArrayList<>();
        int id = 1;
        pool.addAll(criarAtletas(Posicao.GOL, 3, id)); id += 3;
        pool.addAll(criarAtletas(Posicao.LAT, 4, id)); id += 4;
        pool.addAll(criarAtletas(Posicao.ZAG, 4, id)); id += 4;
        pool.addAll(criarAtletas(Posicao.MEI, 5, id)); id += 5;
        pool.addAll(criarAtletas(Posicao.ATA, 5, id)); id += 5;
        pool.addAll(criarAtletas(Posicao.TEC, 3, id));
        return pool;
    }

    private List<Atleta> poolComMuitosAtletasMesmoClube() {
        List<Atleta> pool = new ArrayList<>();
        int clubeDominante = 999;

        pool.add(atletaBuilder(Posicao.GOL, 1, 100.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.GOL, 2, 50.0, 10.0, 1).build());

        pool.add(atletaBuilder(Posicao.LAT, 3, 99.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.LAT, 4, 98.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.LAT, 5, 70.0, 15.0, 2).build());
        pool.add(atletaBuilder(Posicao.LAT, 6, 69.0, 15.0, 3).build());

        pool.add(atletaBuilder(Posicao.ZAG, 7, 97.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.ZAG, 8, 96.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.ZAG, 9, 68.0, 15.0, 4).build());
        pool.add(atletaBuilder(Posicao.ZAG, 10, 67.0, 15.0, 5).build());

        pool.add(atletaBuilder(Posicao.MEI, 11, 95.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.MEI, 12, 94.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.MEI, 13, 93.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.MEI, 14, 66.0, 15.0, 6).build());
        pool.add(atletaBuilder(Posicao.MEI, 15, 65.0, 15.0, 7).build());
        pool.add(atletaBuilder(Posicao.MEI, 16, 64.0, 15.0, 8).build());

        pool.add(atletaBuilder(Posicao.ATA, 17, 92.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.ATA, 18, 91.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.ATA, 19, 90.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.ATA, 20, 63.0, 15.0, 9).build());
        pool.add(atletaBuilder(Posicao.ATA, 21, 62.0, 15.0, 10).build());
        pool.add(atletaBuilder(Posicao.ATA, 22, 61.0, 15.0, 11).build());

        pool.add(atletaBuilder(Posicao.TEC, 23, 89.0, 20.0, clubeDominante).build());
        pool.add(atletaBuilder(Posicao.TEC, 24, 60.0, 15.0, 12).build());
        return pool;
    }

    private List<Atleta> poolComDefensoresRepetidos() {
        List<Atleta> pool = new ArrayList<>();
        pool.add(atletaBuilder(Posicao.GOL, 1, 50.0, 20.0, 10).build());
        pool.add(atletaBuilder(Posicao.GOL, 2, 10.0, 10.0, 11).build());

        pool.add(atletaBuilder(Posicao.LAT, 3, 49.0, 20.0, 10).build());
        pool.add(atletaBuilder(Posicao.LAT, 4, 48.0, 20.0, 12).build());
        pool.add(atletaBuilder(Posicao.LAT, 5, 47.0, 20.0, 13).build());
        pool.add(atletaBuilder(Posicao.LAT, 6, 10.0, 10.0, 14).build());

        pool.add(atletaBuilder(Posicao.ZAG, 7, 46.0, 20.0, 10).build());
        pool.add(atletaBuilder(Posicao.ZAG, 8, 45.0, 20.0, 15).build());
        pool.add(atletaBuilder(Posicao.ZAG, 9, 44.0, 20.0, 16).build());
        pool.add(atletaBuilder(Posicao.ZAG, 10, 10.0, 10.0, 17).build());

        pool.addAll(criarAtletasMesmoClube(Posicao.MEI, 3, 20, 200));
        pool.addAll(criarAtletasMesmoClube(Posicao.ATA, 3, 30, 300));
        pool.addAll(criarAtletasMesmoClube(Posicao.TEC, 1, 40, 400));
        return pool;
    }

    private List<Atleta> poolComDefesaUmSoClube() {
        List<Atleta> pool = new ArrayList<>();
        pool.addAll(criarAtletasMesmoClube(Posicao.GOL, 2, 1,  10));
        pool.addAll(criarAtletasMesmoClube(Posicao.LAT, 4, 10, 10));
        pool.addAll(criarAtletasMesmoClube(Posicao.ZAG, 4, 20, 10));
        pool.addAll(criarAtletasMesmoClube(Posicao.MEI, 3, 30, 200));
        pool.addAll(criarAtletasMesmoClube(Posicao.ATA, 3, 40, 300));
        pool.addAll(criarAtletasMesmoClube(Posicao.TEC, 1, 50, 400));
        return pool;
    }

    private List<Atleta> poolComAtaEmDuvida() {
        var pool = criarPool();
        var ataComMaiorScore = pool.stream()
                .filter(a -> a.getPosicao() == Posicao.ATA)
                .max((a, b) -> Double.compare(a.getScore(), b.getScore()))
                .orElseThrow();
        pool.remove(ataComMaiorScore);
        pool.add(ataComMaiorScore.withStatus(StatusAtleta.DUVIDA));
        return pool;
    }

    private List<Atleta> criarAtletas(Posicao pos, int qtd, int startId) {
        List<Atleta> list = new ArrayList<>();
        for (int i = 0; i < qtd; i++) {
            int id = startId + i;
            list.add(atletaBuilder(pos, id, 10.0 - i, 20.0 - i, id).build());
        }
        return list;
    }

    private List<Atleta> criarAtletasMesmoClube(Posicao pos, int qtd, int startId, int clubeId) {
        List<Atleta> list = new ArrayList<>();
        for (int i = 0; i < qtd; i++) {
            list.add(atletaBuilder(pos, startId + i, 30.0 - i, 20.0 - i, clubeId).build());
        }
        return list;
    }

    private Atleta.AtletaBuilder atletaBuilder(Posicao pos, int id, double score, double preco, int clubeId) {
        return Atleta.builder()
                .atletaId(id)
                .apelido(pos.name() + "_" + id)
                .posicao(pos)
                .clubeId(clubeId)
                .nomeClube("Clube " + clubeId)
                .siglaClube("C" + clubeId)
                .nomeClubeNorm("clube " + clubeId)
                .status(StatusAtleta.PROVAVEL)
                .mediaPontos(score)
                .valorizacao(1.0)
                .preco(preco)
                .desempenhoRecente(0.0)
                .score(score);
    }

    /**
     * Pool para validar o knapsack por orcamento: todas as posicoes (exceto MEI)
     * tem exatamente as vagas necessarias com atletas baratos (score 1 / preco 1),
     * somando C$9.0 fixos. MEI tem 3 premium (score 10 / preco 10) e 3 baratos,
     * forcando a escolha da melhor combinacao dentro do orcamento restante.
     */
    private List<Atleta> poolKnapsackMeio() {
        List<Atleta> pool = new ArrayList<>();
        int id = 1;
        int clube = 1;

        pool.add(atletaBuilder(Posicao.GOL, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.LAT, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.LAT, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.ZAG, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.ZAG, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.ATA, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.ATA, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.ATA, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.TEC, id++, 1.0, 1.0, clube++).build());

        // MEI: 3 premium (score 10 / preco 10) + 3 baratos (score 1 / preco 1)
        pool.add(atletaBuilder(Posicao.MEI, id++, 10.0, 10.0, clube++).build());
        pool.add(atletaBuilder(Posicao.MEI, id++, 10.0, 10.0, clube++).build());
        pool.add(atletaBuilder(Posicao.MEI, id++, 10.0, 10.0, clube++).build());
        pool.add(atletaBuilder(Posicao.MEI, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.MEI, id++, 1.0, 1.0, clube++).build());
        pool.add(atletaBuilder(Posicao.MEI, id, 1.0, 1.0, clube).build());
        return pool;
    }

    /**
     * Pool com dois goleiros de mesmo score (5.0) e precos distintos (C$10.0 e
     * C$3.0). As demais posicoes tem candidatos abundantes e baratos. Com orcamento
     * folgado, o desempate por menor custo deve eleger o goleiro de C$3.0.
     */
    private List<Atleta> poolGolEmpatado() {
        List<Atleta> pool = new ArrayList<>();
        pool.add(atletaBuilder(Posicao.GOL, 1, 5.0, 10.0, 1).build());
        pool.add(atletaBuilder(Posicao.GOL, 2, 5.0,  3.0, 2).build());

        int id = 3;
        int clube = 3;
        for (int i = 0; i < 2; i++) pool.add(atletaBuilder(Posicao.LAT, id++, 4.0, 2.0, clube++).build());
        for (int i = 0; i < 2; i++) pool.add(atletaBuilder(Posicao.ZAG, id++, 4.0, 2.0, clube++).build());
        for (int i = 0; i < 3; i++) pool.add(atletaBuilder(Posicao.MEI, id++, 4.0, 2.0, clube++).build());
        for (int i = 0; i < 3; i++) pool.add(atletaBuilder(Posicao.ATA, id++, 4.0, 2.0, clube++).build());
        pool.add(atletaBuilder(Posicao.TEC, id, 4.0, 2.0, clube).build());
        return pool;
    }

    /** Pool com todos os atletas ao mesmo preco e clubes distintos, scores decrescentes. */
    private List<Atleta> criarPoolPreco(double preco) {
        List<Atleta> pool = new ArrayList<>();
        int id = 1;
        int clube = 1;
        pool.addAll(criarAtletasPreco(Posicao.GOL, 2, preco, id, clube)); id += 2; clube += 2;
        pool.addAll(criarAtletasPreco(Posicao.LAT, 3, preco, id, clube)); id += 3; clube += 3;
        pool.addAll(criarAtletasPreco(Posicao.ZAG, 3, preco, id, clube)); id += 3; clube += 3;
        pool.addAll(criarAtletasPreco(Posicao.MEI, 4, preco, id, clube)); id += 4; clube += 4;
        pool.addAll(criarAtletasPreco(Posicao.ATA, 4, preco, id, clube)); id += 4; clube += 4;
        pool.addAll(criarAtletasPreco(Posicao.TEC, 2, preco, id, clube));
        return pool;
    }

    private List<Atleta> criarAtletasPreco(Posicao pos, int qtd, double preco, int startId, int startClube) {
        List<Atleta> list = new ArrayList<>();
        for (int i = 0; i < qtd; i++) {
            list.add(atletaBuilder(pos, startId + i, 10.0 - i, preco, startClube + i).build());
        }
        return list;
    }

    /**
     * Pool com dois precos: jogadores de maior score custam precoExpensive,
     * jogadores de menor score custam precoBarato. Cada posicao tem jogadores
     * suficientes para preencher a formacao com apenas os baratos.
     */
    private List<Atleta> criarPoolPrecosMistos(double precoExpensive, double precoBarato) {
        List<Atleta> pool = new ArrayList<>();
        int id = 1;

        pool.add(atletaBuilder(Posicao.GOL, id++, 10.0, precoExpensive, 10).build());
        pool.add(atletaBuilder(Posicao.GOL, id++,  5.0, precoBarato,   11).build());
        pool.add(atletaBuilder(Posicao.GOL, id++,  4.0, precoBarato,   12).build());

        pool.add(atletaBuilder(Posicao.LAT, id++,  9.0, precoExpensive, 20).build());
        pool.add(atletaBuilder(Posicao.LAT, id++,  8.0, precoExpensive, 21).build());
        pool.add(atletaBuilder(Posicao.LAT, id++,  4.0, precoBarato,   22).build());
        pool.add(atletaBuilder(Posicao.LAT, id++,  3.0, precoBarato,   23).build());
        pool.add(atletaBuilder(Posicao.LAT, id++,  2.0, precoBarato,   24).build());

        pool.add(atletaBuilder(Posicao.ZAG, id++,  9.0, precoExpensive, 30).build());
        pool.add(atletaBuilder(Posicao.ZAG, id++,  8.0, precoExpensive, 31).build());
        pool.add(atletaBuilder(Posicao.ZAG, id++,  4.0, precoBarato,   32).build());
        pool.add(atletaBuilder(Posicao.ZAG, id++,  3.0, precoBarato,   33).build());
        pool.add(atletaBuilder(Posicao.ZAG, id++,  2.0, precoBarato,   34).build());

        pool.add(atletaBuilder(Posicao.MEI, id++,  9.0, precoExpensive, 40).build());
        pool.add(atletaBuilder(Posicao.MEI, id++,  8.0, precoExpensive, 41).build());
        pool.add(atletaBuilder(Posicao.MEI, id++,  7.0, precoExpensive, 42).build());
        pool.add(atletaBuilder(Posicao.MEI, id++,  4.0, precoBarato,   43).build());
        pool.add(atletaBuilder(Posicao.MEI, id++,  3.0, precoBarato,   44).build());
        pool.add(atletaBuilder(Posicao.MEI, id++,  2.0, precoBarato,   45).build());
        pool.add(atletaBuilder(Posicao.MEI, id++,  1.0, precoBarato,   46).build());

        pool.add(atletaBuilder(Posicao.ATA, id++,  9.0, precoExpensive, 50).build());
        pool.add(atletaBuilder(Posicao.ATA, id++,  8.0, precoExpensive, 51).build());
        pool.add(atletaBuilder(Posicao.ATA, id++,  7.0, precoExpensive, 52).build());
        pool.add(atletaBuilder(Posicao.ATA, id++,  4.0, precoBarato,   53).build());
        pool.add(atletaBuilder(Posicao.ATA, id++,  3.0, precoBarato,   54).build());
        pool.add(atletaBuilder(Posicao.ATA, id++,  2.0, precoBarato,   55).build());
        pool.add(atletaBuilder(Posicao.ATA, id++,  1.0, precoBarato,   56).build());

        pool.add(atletaBuilder(Posicao.TEC, id++,  9.0, precoExpensive, 60).build());
        pool.add(atletaBuilder(Posicao.TEC, id++,  4.0, precoBarato,   61).build());
        pool.add(atletaBuilder(Posicao.TEC, id,    3.0, precoBarato,   62).build());

        return pool;
    }
}
