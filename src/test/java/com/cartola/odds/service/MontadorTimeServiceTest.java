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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
        @DisplayName("reserva de luxo deve ser diferente do capitao")
        void reservaLuxoDeveSerDiferenteDoCapitao() {
            var time = service.montar(criarPool(), 1, null);

            assertThat(time.getReservaLuxo()).isNotNull();
            assertThat(time.getReservaLuxo().getApelido())
                    .isNotEqualTo(time.getCapitao().getApelido());
        }

        @Test
        @DisplayName("reserva de luxo deve ter score menor ou igual ao do capitao")
        void reservaLuxoDeveSerSegundoMaiorScore() {
            var time = service.montar(criarPool(), 1, null);

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
            var time = service.montar(criarPool(), 1, null);

            time.getReservas().forEach((posicao, reserva) ->
                    assertThat(reserva.getPosicao()).isEqualTo(posicao));
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
}
