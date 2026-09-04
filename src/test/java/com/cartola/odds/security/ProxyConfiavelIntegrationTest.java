package com.cartola.odds.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalizacao dos {@code X-Forwarded-*} vinda de um proxy <strong>confiavel</strong>.
 *
 * <p>Atras da borda da plataforma o TLS termina no proxy e o Tomcat veria HTTP puro, entao
 * o HSTS — que o Spring Security so emite quando {@code request.isSecure()} — nunca sairia
 * em producao. Quem corrige e o {@code RemoteIpValve}, ligado por
 * {@code server.forward-headers-strategy=native}.
 *
 * <p>Este teste sobe Tomcat de verdade porque o valve e do Tomcat: no MockMvc ele nao
 * existe, e um caso escrito por la passaria sem exercitar nada. A requisicao sai de
 * {@code 127.0.0.1}, que a faixa padrao de {@code internal-proxies} considera confiavel —
 * e por isso os headers valem.
 *
 * <p>O contraponto, com a mesma requisicao vinda de uma origem nao confiavel, esta no
 * {@link ProxyNaoConfiavelIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
@DisplayName("Seguranca — X-Forwarded-* vindos de proxy confiavel")
class ProxyConfiavelIntegrationTest {

    /** O Spring nao expoe este nome em {@code HttpHeaders}, so os da RFC 7231. */
    private static final String HSTS = "Strict-Transport-Security";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("deve mandar HSTS quando o proxy confiavel diz que a requisicao chegou por TLS")
    void deveEnviarHstsQuandoOProxyConfiavelInformaHttps() {
        var response = health("https");

        assertThat(response.getHeaders().getFirst(HSTS))
                .contains("max-age=31536000")
                .contains("includeSubDomains");
    }

    @Test
    @DisplayName("nao deve mandar HSTS sem o header — travaria o host do desenvolvedor em HTTPS")
    void naoDeveEnviarHstsSemOHeader() {
        var response = health(null);

        assertThat(response.getHeaders().getFirst(HSTS)).isNull();
    }

    /** {@code /actuator/health} e publico, entao nenhum caso aqui precisa de token. */
    private ResponseEntity<String> health(String protocoloEncaminhado) {
        var headers = new HttpHeaders();
        if (protocoloEncaminhado != null) {
            headers.set("X-Forwarded-Proto", protocoloEncaminhado);
        }
        return restTemplate.exchange(
                "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
