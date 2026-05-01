package com.cartola.odds.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

    @Test
    @DisplayName("/actuator/health deve retornar 200")
    void actuatorHealthDeveRetornar200() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/actuator/health deve retornar status UP no corpo")
    void actuatorHealthDeveConterStatusUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    @DisplayName("/actuator/metrics deve retornar 200")
    void actuatorMetricsDeveRetornar200() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/actuator/metrics deve listar nomes de metricas disponiveis")
    void actuatorMetricsDeveListarNomes() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/metrics", String.class);
        assertThat(response.getBody()).contains("names");
    }

    @Test
    @DisplayName("/actuator/prometheus deve retornar 200")
    void actuatorPrometheusDeveRetornar200() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/actuator/prometheus deve retornar conteudo no formato Prometheus")
    void actuatorPrometheusDeveRetornarFormatoPrometheus() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(response.getBody()).contains("application=\"cartolaoddsapi\"");
    }
}
