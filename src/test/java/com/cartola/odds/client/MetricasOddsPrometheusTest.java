package com.cartola.odds.client;

import com.cartola.odds.config.OddsProperties;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os nomes das metricas sao declarados na convencao pontuada do Micrometer
 * ({@code odds.api.requests}), e nao ja no formato final do Prometheus: cada registry aplica a
 * propria traducao, e o codigo nao fica preso ao exporter da vez.
 *
 * <p>O que <em>nao</em> pode mudar e o outro lado dessa traducao. Dashboard, alerta e o README
 * perguntam por {@code odds_api_requests_total}; um upgrade de Micrometer que altere a
 * convencao de nomes quebraria isso em silencio — a metrica continua existindo, com outro nome,
 * e o alerta simplesmente para de disparar. Este teste raspa o registry de verdade e fixa os
 * nomes expostos, para essa mudanca aparecer aqui e nao em producao.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Metricas de odds no Prometheus")
class MetricasOddsPrometheusTest {

    @Mock OddsSnapshotRepository snapshotRepository;
    @Mock OddsCotaRepository     cotaRepository;

    private PrometheusMeterRegistry registry;

    @BeforeEach
    void setUp() {
        var props = new OddsProperties();
        props.setBaseUrl("https://api.the-odds-api.com/v4");
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        new OddsClient(RestClient.builder().baseUrl(props.getBaseUrl()).build(), props,
                snapshotRepository, cotaRepository, new ObjectMapper(), registry);
    }

    @Test
    @DisplayName("deve expor os contadores com o sufixo _total, sem duplicar o que ja esta no nome")
    void deveExporContadores() {
        var scrape = registry.scrape();

        assertThat(scrape).contains("odds_api_requests_total");
        assertThat(scrape).contains("odds_api_errors_total");
        // O nome pontuado nao carrega "total"; se carregasse, a convencao do Prometheus somaria
        // o sufixo por cima e a serie viraria odds_api_requests_total_total.
        assertThat(scrape).doesNotContain("_total_total");
    }

    @Test
    @DisplayName("deve expor o gauge de saldo restante com o nome usado em alerta")
    void deveExporGauge() {
        assertThat(registry.scrape()).contains("odds_api_requests_remaining");
    }
}
