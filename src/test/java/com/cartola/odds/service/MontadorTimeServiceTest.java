package com.cartola.odds.service;

import com.cartola.odds.config.AppProperties;
import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MontadorTimeService")
class MontadorTimeServiceTest {

    private MontadorTimeService service;

    @BeforeEach
    void setUp() {
        var props = new AppProperties();
        props.setFormacao(Map.of(
                "GOL", 1, "LAT", 2, "ZAG", 2,
                "MEI", 3, "ATA", 3, "TEC", 1
        ));
        service = new MontadorTimeService(props);
    }

    @Nested
    @DisplayName("formacao 4-3-3")
    class Formacao {

        @Test
        @DisplayName("deve montar exatamente 1 GOL, 2 LAT, 2 ZAG, 3 MEI, 3 ATA, 1 TEC")
        void deveMontarFormacaoCompleta() {
            var time = service.montar(criarPool(), 10);

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
            var time = service.montar(criarPool(), 22);
            assertThat(time.getRodada()).isEqualTo(22);
        }

        @Test
        @DisplayName("deve selecionar titulares com maior score em cada posicao")
        void deveEscolherMaioresScoresPorPosicao() {
            var pool = criarPool();
            var time = service.montar(pool, 1, null);

            // Para cada posicao, verifica que todos os titulares tem score >= reserva
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
    @DisplayName("capitao")
    class Capitao {

        @Test
        @DisplayName("deve eleger atleta com maior score como capitao")
        void deveElegerCapitaoComMaiorScore() {
            var time = service.montar(criarPool(), 1);

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
            // Cria pool onde o ATA tem score muito alto
            var pool = criarPool();
            var ataComScoreAlto = atletaBuilder(Posicao.ATA, 100, 999.0, 50.0).build();
            pool.add(ataComScoreAlto);

            var time = service.montar(pool, 1, null);

            assertThat(time.getCapitao().getPosicao()).isEqualTo(Posicao.ATA);
        }

        @Test
        @DisplayName("reserva de luxo deve ser diferente do capitao")
        void reservaLuxoDeveSerDiferenteDoCapitao() {
            var time = service.montar(criarPool(), 1);

            assertThat(time.getReservaLuxo()).isNotNull();
            assertThat(time.getReservaLuxo().getApelido())
                    .isNotEqualTo(time.getCapitao().getApelido());
        }

        @Test
        @DisplayName("reserva de luxo deve ter score menor ou igual ao do capitao")
        void reservaLuxoDeveSerSegundoMaiorScore() {
            var time = service.montar(criarPool(), 1);

            assertThat(time.getReservaLuxo().getScore())
                    .isLessThanOrEqualTo(time.getCapitao().getScore());
        }
    }

    @Nested
    @DisplayName("reservas")
    class Reservas {

        @Test
        @DisplayName("reserva deve ser da mesma posicao do titular")
        void reservaDeveSerDaMesmaPosicao() {
            var time = service.montar(criarPool(), 1);

            time.getReservas().forEach((posicao, reserva) ->
                    assertThat(reserva.getPosicao()).isEqualTo(posicao));
        }

        @Test
        @DisplayName("reserva deve ter status PROVAVEL")
        void reservaDeveSerProvavel() {
            var time = service.montar(criarPool(), 1);

            time.getReservas().values().forEach(reserva ->
                    assertThat(reserva.getStatus()).isEqualTo(StatusAtleta.PROVAVEL));
        }

        @Test
        @DisplayName("reserva nao deve ser o mesmo atleta do titular")
        void reservaNaoDeveSerTitular() {
            var time = service.montar(criarPool(), 1);

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
            var time = service.montar(criarPool(), 1);

            time.getReservas().forEach((pos, reserva) -> {
                double maxPrecoTitular = time.getTitulares().get(pos).stream()
                        .mapToDouble(Atleta::getPreco)
                        .max().orElse(Double.MAX_VALUE);
                // reserva com preco menor OU igual (fallback sem restricao de preco)
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
            var pool = poolComAtaEmDuvida();
            var time = service.montar(pool, 1, null);

            var titularesAta = time.getTitulares().get(Posicao.ATA);
            var emDuvida = titularesAta.stream().filter(Atleta::isDuvida).findFirst();

            assertThat(emDuvida).isPresent();
            assertThat(emDuvida.get().getSubstitutoProvavel()).isNotNull();
        }

        @Test
        @DisplayName("substituto deve ser da MESMA posicao individual do titular em duvida")
        void substitutoDeveSerDaMesmaPosicao() {
            var pool = poolComAtaEmDuvida();
            var time = service.montar(pool, 1, null);

            time.getTitulares().get(Posicao.ATA).stream()
                    .filter(Atleta::isDuvida)
                    .forEach(j -> {
                        assertThat(j.getSubstitutoProvavel()).isNotNull();
                        assertThat(j.getSubstitutoProvavel().getPosicao())
                                .isEqualTo(Posicao.ATA);
                    });
        }

        @Test
        @DisplayName("substituto nao deve ser outro titular ja escalado")
        void substitutoNaoDeveSerTitular() {
            var pool = poolComAtaEmDuvida();
            var time = service.montar(pool, 1, null);

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
            var pool = poolComAtaEmDuvida();
            var time = service.montar(pool, 1, null);

            assertThat(time.getAlertasDuvida()).isNotEmpty();
        }

        @Test
        @DisplayName("titular provavel nao deve ter substituto preenchido")
        void titularProvavelNaoDeveTermSubstituto() {
            var time = service.montar(criarPool(), 1);

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
            var time = service.montar(criarPool(), 1);

            double esperado = time.getTitulares().values().stream()
                    .flatMap(List::stream)
                    .mapToDouble(Atleta::getPreco)
                    .sum();

            assertThat(time.getCustoTotal()).isEqualTo(esperado);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

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
            list.add(atletaBuilder(pos, startId + i, 10.0 - i, 20.0 - i).build());
        }
        return list;
    }

    private Atleta.AtletaBuilder atletaBuilder(Posicao pos, int id, double score, double preco) {
        return Atleta.builder()
                .apelido(pos.name() + "_" + id)
                .posicao(pos)
                .clubeId(id)
                .nomeClube("Clube " + id)
                .siglaClube("C" + id)
                .nomeClubeNorm("clube " + id)
                .atletaId(startId + i)
                    .status(StatusAtleta.PROVAVEL)
                .mediaPontos(score)
                .valorizacao(1.0)
                .preco(preco)
                .desempenhoRecente(0.0)
                .score(score);
    }
}
