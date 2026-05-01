package com.cartola.odds.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
@DisplayName("Actuator Endpoints")
class ActuatorEndpointsTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("/actuator/health deve retornar 200")
    void actuatorHealthDeveRetornar200() {
        ResponseEntity<String> response = restTemplate.getForEntity(actuatorUrl("/health"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/actuator/health deve retornar status UP no corpo")
    void actuatorHealthDeveConterStatusUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(actuatorUrl("/health"), String.class);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    @DisplayName("/actuator/metrics deve retornar 200")
    void actuatorMetricsDeveRetornar200() {
        ResponseEntity<String> response = restTemplate.getForEntity(actuatorUrl("/metrics"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/actuator/metrics deve listar nomes de metricas disponiveis")
    void actuatorMetricsDeveListarNomes() {
        ResponseEntity<String> response = restTemplate.getForEntity(actuatorUrl("/metrics"), String.class);
        assertThat(response.getBody()).contains("names");
    }

    @Test
    @DisplayName("/actuator/metrics/{nome} deve retornar detalhe de uma metrica")
    void actuatorMetricsNomeDeveRetornarDetalhe() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                actuatorUrl("/metrics/jvm.memory.used"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm.memory.used");
    }

    @Test
    @DisplayName("/actuator/prometheus deve retornar 200")
    void actuatorPrometheusDeveRetornar200() {
        ResponseEntity<String> response = restTemplate.getForEntity(actuatorUrl("/prometheus"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/actuator/prometheus deve retornar conteudo no formato Prometheus")
    void actuatorPrometheusDeveRetornarFormatoPrometheus() {
        ResponseEntity<String> response = restTemplate.getForEntity(actuatorUrl("/prometheus"), String.class);
        assertThat(response.getBody()).contains("application=\"cartolaoddsapi\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/env", "/beans", "/heapdump"})
    @DisplayName("endpoints sensiveis do Actuator nao devem estar expostos")
    void actuatorEndpointsSensiveisNaoDevemEstarExpostos(String endpoint) {
        ResponseEntity<String> response = restTemplate.getForEntity(actuatorUrl(endpoint), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String actuatorUrl(String path) {
        return "http://localhost:" + managementPort + "/actuator" + path;
    }
}
