package com.cartola.odds.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.OddsCota;
import com.cartola.odds.model.OddsSnapshot;
import com.cartola.odds.repository.OddsCotaRepository;
import com.cartola.odds.repository.OddsSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Guardrail de cota (#40).
 *
 * <p>Onde um caso <strong>nao</strong> registra expectativa no {@link MockRestServiceServer},
 * a ausencia e a propria assercao: uma chamada inesperada ao provedor faz o mock falhar na
 * hora. E o que prova "nao gastou cota" sem depender de um contador.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OddsClient")
class OddsClientTest {

    private static final String JOGO_JSON = """
            [{"id":"1","home_team":"Flamengo","away_team":"Palmeiras","bookmakers":[]}]
            """;

    @Mock OddsSnapshotRepository snapshotRepository;
    @Mock OddsCotaRepository     cotaRepository;

    private MockRestServiceServer server;
    private OddsClient            oddsClient;
    private OddsProperties        props;
    private SimpleMeterRegistry   meterRegistry;

    @BeforeEach
    void setUp() {
        props = new OddsProperties();
        props.setKey("TEST_KEY");
        props.setBaseUrl("https://api.the-odds-api.com/v4");
        props.setSport("soccer_brazil_campeonato");
        props.setRegions("us");
        props.setMarkets("h2h");
        props.setMinRequestsRemaining(50);
        props.setCacheTtlMinutos(60);
        props.setCacheTtlDegradadoMinutos(10);
        props.setSondaIntervaloHoras(24);

        var builder = RestClient.builder().baseUrl(props.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        meterRegistry = new SimpleMeterRegistry();

        oddsClient = new OddsClient(builder.build(), props, snapshotRepository, cotaRepository,
                new ObjectMapper(), meterRegistry);
    }

    @Nested
    @DisplayName("leitura dos headers de cota")
    class LeituraDeHeaders {

        @BeforeEach
        void semSnapshot() {
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("deve expor saldo e consumo lidos dos headers x-requests-remaining/x-requests-used")
        void deveExporSaldoEConsumo() {
            responderComCota("412", "88");

            oddsClient.buscarOdds();

            assertThat(oddsClient.getRequestsRemaining()).isEqualTo(412L);
            assertThat(oddsClient.getRequestsUsed()).isEqualTo(88L);
            assertThat(oddsClient.getUltimaLeitura()).isNotNull();
            assertThat(oddsClient.isGuardrailAtivo()).isFalse();
        }

        @Test
        @DisplayName("deve manter saldo nulo quando a resposta nao traz os headers de cota")
        void deveManterNuloSemHeaders() {
            server.expect(requestTo(containsString("/odds")))
                    .andRespond(withSuccess(JOGO_JSON, MediaType.APPLICATION_JSON));

            oddsClient.buscarOdds();

            assertThat(oddsClient.getRequestsRemaining()).isNull();
            assertThat(oddsClient.getRequestsUsed()).isNull();
        }

        @Test
        @DisplayName("nao deve marcar leitura de cota quando a resposta nao traz nenhum header")
        void naoDeveMarcarLeituraSemHeaders() {
            // Marcar assim mesmo empurraria a proxima sondagem por um intervalo inteiro em
            // troca de nada, mantendo o guardrail barrando com um saldo que ninguem conferiu.
            server.expect(requestTo(containsString("/odds")))
                    .andRespond(withSuccess(JOGO_JSON, MediaType.APPLICATION_JSON));

            oddsClient.buscarOdds();

            assertThat(oddsClient.getUltimaLeitura()).isNull();
        }

        @Test
        @DisplayName("deve ler o saldo dos headers tambem na resposta de erro do provedor")
        void deveLerSaldoNaRespostaDeErro() {
            // Cota estourada nao chega como 200: o provedor recusa a chamada e e nessa resposta
            // que o saldo real aparece. Sem ler aqui, o guardrail nunca armaria.
            server.expect(requestTo(containsString("/odds")))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(cota("3", "497")));

            oddsClient.buscarOdds();

            assertThat(oddsClient.getRequestsRemaining()).isEqualTo(3L);
            assertThat(oddsClient.getRequestsUsed()).isEqualTo(497L);
            assertThat(oddsClient.isGuardrailAtivo()).isTrue();
        }

        @Test
        @DisplayName("deve ignorar header de cota com valor nao numerico, sem quebrar a busca")
        void deveIgnorarHeaderInvalido() {
            responderComCota("nao-e-numero", "88");

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.odds()).hasSize(1);
            assertThat(oddsClient.getRequestsRemaining()).isNull();
            assertThat(oddsClient.getRequestsUsed()).isEqualTo(88L);
        }
    }

    @Nested
    @DisplayName("atalho de snapshot no boot")
    class AtalhoDeBoot {

        @Test
        @DisplayName("deve servir o snapshot dentro do TTL na primeira busca, sem chamar o provedor")
        void deveServirSnapshotNaPrimeiraBusca() {
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusMinutes(5));

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.odds()).hasSize(1);
            assertThat(resultado.deSnapshot()).isTrue();
        }

        @Test
        @DisplayName("deve chamar o provedor na busca seguinte, mesmo com snapshot fresco — e o DELETE /api/cache")
        void deveChamarProvedorNaSegundaBusca() {
            // O atalho existe para o cache frio de um restart. Uma segunda busca so acontece
            // quando o TTL venceu ou quando alguem limpou o cache de proposito, e nos dois
            // casos servir o snapshot de novo tornaria DELETE /api/cache um comando sem efeito.
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusMinutes(5));
            oddsClient.buscarOdds();

            responderComCota("412", "88");
            var resultado = oddsClient.buscarOdds();

            server.verify();
            assertThat(resultado.deSnapshot()).isFalse();
        }

        @Test
        @DisplayName("deve chamar o provedor ja na primeira busca quando o snapshot esta alem do TTL")
        void deveChamarProvedorComSnapshotVencido() {
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusMinutes(120));
            responderComCota("300", "200");

            var resultado = oddsClient.buscarOdds();

            server.verify();
            assertThat(resultado.deSnapshot()).isFalse();
        }
    }

    @Nested
    @DisplayName("guardrail de cota")
    class Guardrail {

        @Test
        @DisplayName("nao deve chamar o provedor quando o saldo conhecido esta abaixo do minimo")
        void naoDeveChamarProvedorComSaldoBaixo() {
            comSaldoConhecido(30);
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusDays(1));

            var resultado = oddsClient.buscarOdds();

            assertThat(oddsClient.isGuardrailAtivo()).isTrue();
            assertThat(resultado.deSnapshot()).isTrue();
            assertThat(resultado.odds()).hasSize(1);
        }

        @Test
        @DisplayName("deve barrar tambem a busca disparada por limpeza manual do cache")
        void deveBarrarAposLimpezaDeCache() {
            // DELETE /api/cache passa a respeitar o mesmo limite: sem isto, o gatilho manual
            // seria uma porta lateral para furar o guardrail.
            comSaldoConhecido(30);
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusDays(1));

            oddsClient.buscarOdds();
            var segunda = oddsClient.buscarOdds();

            assertThat(segunda.deSnapshot()).isTrue();
        }

        @Test
        @DisplayName("deve devolver lista vazia quando o guardrail esta ativo e nao ha snapshot")
        void deveDevolverVazioSemSnapshot() {
            comSaldoConhecido(10);

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.odds()).isEmpty();
            assertThat(resultado.deSnapshot()).isFalse();
        }

        @Test
        @DisplayName("deve liberar uma sondagem quando a ultima leitura de cota ja esta velha")
        void deveLiberarSondagem() {
            // Sem sondagem o guardrail se auto-alimenta: barra, o saldo nunca e reavaliado,
            // continua barrando — e a virada de mes que renova a cota so apareceria num restart.
            props.setSondaIntervaloHoras(0);
            comSaldoConhecido(30);
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusDays(1));

            responderComCota("500", "0");
            var resultado = oddsClient.buscarOdds();

            server.verify();
            assertThat(resultado.deSnapshot()).isFalse();
            assertThat(oddsClient.getRequestsRemaining()).isEqualTo(500L);
            assertThat(oddsClient.isGuardrailAtivo()).isFalse();
        }

        @Test
        @DisplayName("nao deve sondar enquanto a leitura de cota ainda esta dentro do intervalo")
        void naoDeveSondarDentroDoIntervalo() {
            // A sondagem e a valvula do guardrail, nao um furo nele: com o saldo lido ha
            // instantes, nenhuma chamada nova se justifica.
            comSaldoConhecido(30);
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusDays(1));

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.deSnapshot()).isTrue();
        }
    }

    @Nested
    @DisplayName("fallback por falha do provedor")
    class FalhaDoProvedor {

        @Test
        @DisplayName("deve servir o snapshot persistido quando o provedor responde erro")
        void deveServirSnapshotAposErro() {
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusMinutes(120));
            server.expect(requestTo(containsString("/odds"))).andRespond(withServerError());

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.odds()).hasSize(1);
            assertThat(resultado.deSnapshot()).isTrue();
        }

        @Test
        @DisplayName("deve devolver lista vazia quando o provedor falha e nao ha snapshot")
        void deveDevolverVazioSemSnapshotAposErro() {
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
            server.expect(requestTo(containsString("/odds"))).andRespond(withServerError());

            assertThat(oddsClient.buscarOdds().odds()).isEmpty();
        }

        @Test
        @DisplayName("deve devolver lista vazia quando o snapshot persistido esta corrompido")
        void deveDevolverVazioComSnapshotCorrompido() {
            comSnapshot("{ isto nao e uma lista de odds", LocalDateTime.now().minusMinutes(5));

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.odds()).isEmpty();
            assertThat(resultado.deSnapshot()).isTrue();
        }
    }

    @Nested
    @DisplayName("persistencia do snapshot")
    class PersistenciaDoSnapshot {

        @Test
        @DisplayName("deve persistir o snapshot quando o provedor responde com jogos")
        void devePersistirComJogos() {
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
            responderComCota("412", "88");

            oddsClient.buscarOdds();

            verify(snapshotRepository).save(any(OddsSnapshot.class));
        }

        @Test
        @DisplayName("nao deve sobrescrever o snapshot quando o provedor responde sem nenhum jogo")
        void naoDeveSobrescreverComRespostaVazia() {
            // Uma lista vazia gravada por cima destruiria o unico fallback que o guardrail tem
            // para servir depois — e a The Odds API responde 200 com [] fora de temporada.
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusMinutes(120));
            server.expect(requestTo(containsString("/odds")))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON).headers(cota("412", "88")));

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.odds()).isEmpty();
            verify(snapshotRepository, never()).save(any(OddsSnapshot.class));
        }
    }

    @Nested
    @DisplayName("estado da cota entre restarts")
    class CotaPersistida {

        @Test
        @DisplayName("deve recuperar o saldo persistido no boot, mantendo o guardrail armado apos o deploy")
        void deveRecuperarSaldoNoBoot() {
            // Sem isto o guardrail nasce desarmado a cada deploy — e e no deploy que o cache
            // em memoria some, ou seja, exatamente quando a proxima requisicao quer chamar.
            when(cotaRepository.findById(OddsCota.ID_UNICO)).thenReturn(Optional.of(
                    cotaPersistida(20L, 480L, LocalDateTime.now().minusMinutes(5))));
            comSnapshot(JOGO_JSON, LocalDateTime.now().minusDays(1));

            oddsClient.carregarCotaPersistida();
            var resultado = oddsClient.buscarOdds();

            assertThat(oddsClient.getRequestsRemaining()).isEqualTo(20L);
            assertThat(oddsClient.isGuardrailAtivo()).isTrue();
            assertThat(resultado.deSnapshot()).isTrue();
        }

        @Test
        @DisplayName("deve persistir o estado da cota ao ler os headers do provedor")
        void devePersistirEstadoDaCota() {
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
            responderComCota("412", "88");

            oddsClient.buscarOdds();

            verify(cotaRepository).save(any(OddsCota.class));
        }

        @Test
        @DisplayName("deve subir sem saldo conhecido quando o banco falha na recuperacao")
        void deveTolerarFalhaAoRecuperar() {
            when(cotaRepository.findById(OddsCota.ID_UNICO))
                    .thenThrow(new DataAccessResourceFailureException("banco fora"));

            oddsClient.carregarCotaPersistida();

            assertThat(oddsClient.getRequestsRemaining()).isNull();
            assertThat(oddsClient.isGuardrailAtivo()).isFalse();
        }

        @Test
        @DisplayName("nao deve quebrar a busca quando a leitura do snapshot falha no banco")
        void deveTolerarFalhaAoLerSnapshot() {
            // O fallback existe para degradar com elegancia; nao pode ser ele proprio a virar 500.
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO))
                    .thenThrow(new DataAccessResourceFailureException("banco fora"));
            responderComCota("412", "88");

            var resultado = oddsClient.buscarOdds();

            assertThat(resultado.odds()).hasSize(1);
            assertThat(resultado.deSnapshot()).isFalse();
        }

        private OddsCota cotaPersistida(Long remaining, Long used, LocalDateTime leitura) {
            var cota = new OddsCota();
            cota.setId(OddsCota.ID_UNICO);
            cota.setRequestsRemaining(remaining);
            cota.setRequestsUsed(used);
            cota.setUltimaLeitura(leitura);
            return cota;
        }
    }

    @Nested
    @DisplayName("metricas")
    class Metricas {

        @Test
        @DisplayName("gauge de saldo deve ficar NaN enquanto nao houve leitura, para nao disparar alerta no deploy")
        void gaugeDeveSerNaNSemLeitura() {
            assertThat(saldoNoGauge()).isNaN();
        }

        @Test
        @DisplayName("gauge de saldo deve refletir o valor lido do provedor")
        void gaugeDeveRefletirSaldo() {
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
            responderComCota("412", "88");

            oddsClient.buscarOdds();

            assertThat(saldoNoGauge()).isEqualTo(412.0);
        }

        @Test
        @DisplayName("deve contar chamadas feitas e erros do provedor")
        void deveContarChamadasEErros() {
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
            responderComCota("412", "88");
            oddsClient.buscarOdds();

            server.reset();
            server.expect(requestTo(containsString("/odds"))).andRespond(withServerError());
            oddsClient.buscarOdds();

            assertThat(meterRegistry.counter("odds_api_requests_total").count()).isEqualTo(1.0);
            assertThat(meterRegistry.counter("odds_api_errors_total").count()).isEqualTo(1.0);
        }

        private double saldoNoGauge() {
            return meterRegistry.get("odds_api_requests_remaining").gauge().value();
        }
    }

    @Nested
    @DisplayName("limiares de aviso")
    class LimiaresDeAviso {

        @Test
        @DisplayName("deve avisar ao cruzar o dobro do minimo e depois o proprio minimo configurado")
        void deveAvisarNosLimiaresDerivadosDoMinimo() {
            // Limiares fixos em 100/50 deixariam um min-requests-remaining=100 acionar o
            // guardrail sem nenhum aviso previo.
            props.setMinRequestsRemaining(100);
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());

            var appender = capturarAvisos();
            try {
                responderComCota("250", "250");
                oddsClient.buscarOdds();
                assertThat(avisos(appender)).isEmpty();

                server.reset();
                responderComCota("150", "350");
                oddsClient.buscarOdds();
                assertThat(avisos(appender)).hasSize(1);
                assertThat(avisos(appender).get(0)).contains("cruzou 200");

                server.reset();
                responderComCota("90", "410");
                oddsClient.buscarOdds();
                assertThat(avisos(appender)).hasSize(2);
                assertThat(avisos(appender).get(1)).contains("cruzou 100");
            } finally {
                pararCaptura(appender);
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    /** Deixa um saldo abaixo do minimo ja conhecido, como se uma chamada anterior o tivesse lido. */
    private void comSaldoConhecido(long remanescente) {
        when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
        responderComCota(String.valueOf(remanescente), "10");
        oddsClient.buscarOdds();
        server.reset();
    }

    private void comSnapshot(String json, LocalDateTime criadoEm) {
        var snapshot = new OddsSnapshot();
        snapshot.setId(OddsSnapshot.ID_UNICO);
        snapshot.setOddsJson(json);
        snapshot.setCriadoEm(criadoEm);
        when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.of(snapshot));
    }

    private void responderComCota(String remaining, String used) {
        server.expect(requestTo(containsString("/sports/soccer_brazil_campeonato/odds")))
                .andRespond(withSuccess(JOGO_JSON, MediaType.APPLICATION_JSON).headers(cota(remaining, used)));
    }

    private HttpHeaders cota(String remaining, String used) {
        var headers = new HttpHeaders();
        headers.add("x-requests-remaining", remaining);
        headers.add("x-requests-used", used);
        return headers;
    }

    private ListAppender<ILoggingEvent> capturarAvisos() {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(OddsClient.class)).addAppender(appender);
        return appender;
    }

    private void pararCaptura(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(OddsClient.class)).detachAppender(appender);
    }

    private List<String> avisos(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("cruzou"))
                .toList();
    }
}
