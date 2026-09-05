package com.cartola.odds.client;

import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.OddsSnapshot;
import com.cartola.odds.model.response.OddsResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Guardrail de cota (#40): leitura dos headers de saldo, contagem de chamadas/erros,
 * queda para o snapshot persistido quando o guardrail entra em acao ou o provedor falha,
 * e uso do snapshot sem chamar o provedor quando ele ainda esta dentro do TTL do cache.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OddsClient")
class OddsClientTest {

    private static final String JOGO_JSON = """
            [{"id":"1","home_team":"Flamengo","away_team":"Palmeiras","bookmakers":[]}]
            """;

    @Mock OddsSnapshotRepository snapshotRepository;

    private MockRestServiceServer server;
    private OddsClient            oddsClient;
    private OddsProperties        props;

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

        var builder = RestClient.builder().baseUrl(props.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        var restClient = builder.build();

        oddsClient = new OddsClient(restClient, props, snapshotRepository, new ObjectMapper(), new SimpleMeterRegistry());
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
            server.expect(requestTo(org.hamcrest.Matchers.containsString("/sports/soccer_brazil_campeonato/odds")))
                    .andRespond(withSuccess(JOGO_JSON, MediaType.APPLICATION_JSON)
                            .headers(headers("412", "88")));

            oddsClient.buscarOdds();

            assertThat(oddsClient.getRequestsRemaining()).isEqualTo(412L);
            assertThat(oddsClient.getRequestsUsed()).isEqualTo(88L);
            assertThat(oddsClient.getUltimaLeitura()).isNotNull();
            assertThat(oddsClient.isGuardrailAtivo()).isFalse();
        }

        @Test
        @DisplayName("deve manter saldo nulo quando a resposta nao traz os headers de cota")
        void deveManterNuloSemHeaders() {
            server.expect(requestTo(org.hamcrest.Matchers.containsString("/odds")))
                    .andRespond(withSuccess(JOGO_JSON, MediaType.APPLICATION_JSON));

            oddsClient.buscarOdds();

            assertThat(oddsClient.getRequestsRemaining()).isNull();
            assertThat(oddsClient.getRequestsUsed()).isNull();
        }
    }

    @Nested
    @DisplayName("guardrail de cota")
    class Guardrail {

        @Test
        @DisplayName("nao deve chamar o provedor quando o saldo conhecido esta abaixo do minimo")
        void naoDeveChamarProvedorComSaldoBaixo() throws Exception {
            simularSaldoConhecido(30);
            var snapshot = snapshotDe("[]", LocalDateTime.now().minusDays(1));
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.of(snapshot));

            oddsClient.buscarOdds();

            server.verify();
            assertThat(oddsClient.isGuardrailAtivo()).isTrue();
        }

        @Test
        @DisplayName("deve servir o snapshot persistido quando o guardrail esta ativo")
        void deveServirSnapshotComGuardrailAtivo() throws Exception {
            simularSaldoConhecido(10);
            var snapshot = snapshotDe(JOGO_JSON, LocalDateTime.now().minusDays(1));
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.of(snapshot));

            List<OddsResponse> odds = oddsClient.buscarOdds();

            assertThat(odds).hasSize(1);
            assertThat(oddsClient.isVindoDeSnapshot()).isTrue();
        }

        @Test
        @DisplayName("deve devolver lista vazia quando o guardrail esta ativo e nao ha snapshot")
        void deveDevolverVazioSemSnapshot() {
            simularSaldoConhecido(10);
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());

            assertThat(oddsClient.buscarOdds()).isEmpty();
            server.verify();
        }

        private void simularSaldoConhecido(long remanescente) {
            server.expect(requestTo(org.hamcrest.Matchers.containsString("/odds")))
                    .andRespond(withSuccess(JOGO_JSON, MediaType.APPLICATION_JSON)
                            .headers(headers(String.valueOf(remanescente), "10")));
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());
            oddsClient.buscarOdds();
            server.reset();
        }
    }

    @Nested
    @DisplayName("fallback por falha do provedor")
    class FalhaDoProvedor {

        @Test
        @DisplayName("deve servir o snapshot persistido quando o provedor responde erro")
        void deveServirSnapshotAposErro() throws Exception {
            var snapshot = snapshotDe(JOGO_JSON, LocalDateTime.now().minusDays(1));
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.of(snapshot));

            server.expect(requestTo(org.hamcrest.Matchers.containsString("/odds")))
                    .andRespond(withServerError());

            List<OddsResponse> odds = oddsClient.buscarOdds();

            assertThat(odds).hasSize(1);
            assertThat(oddsClient.isVindoDeSnapshot()).isTrue();
        }

        @Test
        @DisplayName("deve devolver lista vazia quando o provedor falha e nao ha snapshot")
        void deveDevolverVazioSemSnapshotAposErro() {
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.empty());

            server.expect(requestTo(org.hamcrest.Matchers.containsString("/odds")))
                    .andRespond(withServerError());

            assertThat(oddsClient.buscarOdds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("snapshot dentro do TTL")
    class SnapshotDentroDoTtl {

        @Test
        @DisplayName("nao deve chamar o provedor quando o snapshot ainda esta dentro do TTL do cache")
        void naoDeveChamarProvedorComSnapshotFresco() throws Exception {
            var snapshot = snapshotDe(JOGO_JSON, LocalDateTime.now().minusMinutes(5));
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.of(snapshot));

            List<OddsResponse> odds = oddsClient.buscarOdds();

            server.verify();
            assertThat(odds).hasSize(1);
            assertThat(oddsClient.isVindoDeSnapshot()).isTrue();
        }

        @Test
        @DisplayName("deve chamar o provedor quando o snapshot esta alem do TTL do cache")
        void deveChamarProvedorComSnapshotVencido() throws Exception {
            var snapshot = snapshotDe(JOGO_JSON, LocalDateTime.now().minusMinutes(120));
            when(snapshotRepository.findById(OddsSnapshot.ID_UNICO)).thenReturn(Optional.of(snapshot));

            server.expect(requestTo(org.hamcrest.Matchers.containsString("/odds")))
                    .andRespond(withSuccess(JOGO_JSON, MediaType.APPLICATION_JSON)
                            .headers(headers("300", "200")));

            oddsClient.buscarOdds();

            server.verify();
            assertThat(oddsClient.isVindoDeSnapshot()).isFalse();
        }
    }

    private OddsSnapshot snapshotDe(String json, LocalDateTime criadoEm) {
        var snapshot = new OddsSnapshot();
        snapshot.setId(OddsSnapshot.ID_UNICO);
        snapshot.setOddsJson(json);
        snapshot.setCriadoEm(criadoEm);
        return snapshot;
    }

    private org.springframework.http.HttpHeaders headers(String remaining, String used) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.add("x-requests-remaining", remaining);
        headers.add("x-requests-used", used);
        return headers;
    }
}
