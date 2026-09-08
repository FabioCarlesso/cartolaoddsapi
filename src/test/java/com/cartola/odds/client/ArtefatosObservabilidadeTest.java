package com.cartola.odds.client;

import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.repository.OddsCotaHistoricoRepository;
import com.cartola.odds.repository.OddsCotaRepository;
import com.cartola.odds.repository.OddsSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O dashboard e as regras de alerta de {@code docs/observabilidade/} (#58) sao arquivos de
 * texto que ninguem compila: eles perguntam por nomes de metrica e por um limiar que vivem na
 * aplicacao, e quando um dos dois lados muda o outro nao reclama — o painel fica vazio e o
 * alerta simplesmente para de disparar, que e a falha mais silenciosa possivel num artefato
 * cujo trabalho e avisar.
 *
 * <p>Este teste amarra os dois lados. Ele raspa o registry de verdade e confere que todo nome
 * {@code odds_api_*} citado nos artefatos existe na exposicao, e que o corte usado nas regras
 * e no dashboard e o mesmo {@code odds.api.min-requests-remaining} do
 * {@code application.properties}.
 *
 * @see MetricasOddsPrometheusTest que fixa a traducao dos nomes do Micrometer para o Prometheus
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Artefatos de observabilidade versionados")
class ArtefatosObservabilidadeTest {

    private static final Path DIRETORIO  = Path.of("docs", "observabilidade");
    private static final Path ALERTAS    = DIRETORIO.resolve("alertas-cota-odds.yml");
    private static final Path DASHBOARD  = DIRETORIO.resolve("grafana-cota-odds.json");
    private static final Path PROPRIEDADES =
            Path.of("src", "main", "resources", "application.properties");

    /** Nomes de metrica na convencao do Prometheus, do jeito que os artefatos os escrevem. */
    private static final Pattern NOME_DE_METRICA = Pattern.compile("odds_api_[a-z_]+");

    @Mock OddsSnapshotRepository      snapshotRepository;
    @Mock OddsCotaRepository          cotaRepository;
    @Mock OddsCotaHistoricoRepository historicoRepository;

    private PrometheusMeterRegistry registry;

    @BeforeEach
    void setUp() {
        var props = new OddsProperties();
        props.setBaseUrl("https://api.the-odds-api.com/v4");
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        new OddsClient(RestClient.builder().baseUrl(props.getBaseUrl()).build(), props,
                snapshotRepository, cotaRepository, historicoRepository, new ObjectMapper(), registry);
    }

    @Test
    @DisplayName("deve perguntar so por metricas que a aplicacao expoe de fato")
    void metricasCitadasDevemExistir() throws IOException {
        var scrape = registry.scrape();
        var citadas = metricasCitadasEm(ALERTAS, DASHBOARD);

        // Sem esta guarda o teste passaria por engano se os arquivos mudassem de lugar:
        // nenhum nome encontrado, nenhuma assercao feita, tudo verde.
        assertThat(citadas)
                .as("nomes odds_api_* encontrados em %s e %s", ALERTAS, DASHBOARD)
                .hasSizeGreaterThanOrEqualTo(3);

        assertThat(citadas).allSatisfy(nome -> assertThat(scrape)
                .as("metrica '%s', citada nos artefatos de observabilidade, precisa existir na exposicao", nome)
                .contains(nome));
    }

    @Test
    @DisplayName("deve usar o mesmo minimo do guardrail configurado na aplicacao")
    void limiarDeveAcompanharAConfiguracao() throws IOException {
        // O minimo do guardrail nao e exportado como metrica, entao os artefatos precisam
        // repeti-lo. Repetido, ele pode divergir — e um alerta que descreve um corte que a
        // aplicacao nao aplica e pior do que nenhum alerta.
        var minimo = minimoConfigurado();

        assertThat(ler(ALERTAS))
                .as("as regras de alerta precisam comparar o saldo com o minimo configurado (%s)", minimo)
                .contains("odds_api_requests_remaining < " + minimo);

        assertThat(ler(DASHBOARD))
                .as("a variavel 'minimo' do dashboard precisa comecar no minimo configurado (%s)", minimo)
                .contains("\"name\": \"minimo\"")
                .contains("\"query\": \"" + minimo + "\"");
    }

    // ── Privado ───────────────────────────────────────────────────────

    /** Le {@code odds.api.min-requests-remaining=${ODDS_API_MIN_REQUESTS_REMAINING:50}} → {@code 50}. */
    private static String minimoConfigurado() throws IOException {
        var matcher = Pattern.compile("odds\\.api\\.min-requests-remaining=.*?(\\d+)}?\\s*$",
                Pattern.MULTILINE).matcher(ler(PROPRIEDADES));
        assertThat(matcher.find())
                .as("propriedade odds.api.min-requests-remaining em %s", PROPRIEDADES)
                .isTrue();
        return matcher.group(1);
    }

    private static Set<String> metricasCitadasEm(Path... arquivos) throws IOException {
        Set<String> nomes = new TreeSet<>();
        for (Path arquivo : arquivos) {
            Matcher matcher = NOME_DE_METRICA.matcher(ler(arquivo));
            while (matcher.find()) {
                nomes.add(matcher.group());
            }
        }
        return nomes;
    }

    private static String ler(Path arquivo) throws IOException {
        assertThat(arquivo).as("artefato versionado de observabilidade").exists();
        return Files.readString(arquivo, StandardCharsets.UTF_8);
    }
}
