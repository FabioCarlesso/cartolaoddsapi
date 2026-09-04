package com.cartola.odds.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os {@code X-Forwarded-*} enviados por quem <strong>nao</strong> e proxy confiavel devem
 * ser ignorados.
 *
 * <p>E a razao de a aplicacao usar {@code server.forward-headers-strategy=native} e nao
 * {@code framework}: o {@code ForwardedHeaderFilter} do framework acredita nesses headers
 * venham eles de onde vierem, e um cliente qualquer reescreveria esquema e host da propria
 * requisicao — um {@code X-Forwarded-Host: evil.example} saindo no {@code Location} de uma
 * resposta {@code 201}, por exemplo. O {@code RemoteIpValve} so os aplica quando a conexao
 * vem de um endereco listado em {@code server.tomcat.remoteip.internal-proxies}.
 *
 * <p>O teste inverte a lista para provar isso: aqui o unico proxy confiavel e
 * {@code 10.99.99.99}, entao a requisicao do {@code TestRestTemplate} — que sai de
 * {@code 127.0.0.1} — deixa de ser confiavel, e o {@code X-Forwarded-Proto: https} nao
 * consegue mais forjar um {@code request.isSecure()} verdadeiro. Sem a inversao o caso nao
 * teria como existir: em {@code localhost} toda requisicao vem de uma faixa confiavel.
 *
 * <p>O caminho feliz — o proxy confiavel sendo obedecido — esta no
 * {@link ProxyConfiavelIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4",
        "server.tomcat.remoteip.internal-proxies=10\\.99\\.99\\.99"
})
@DisplayName("Seguranca — X-Forwarded-* vindos de origem nao confiavel")
class ProxyNaoConfiavelIntegrationTest {

    /** O Spring nao expoe este nome em {@code HttpHeaders}, so os da RFC 7231. */
    private static final String HSTS = "Strict-Transport-Security";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("nao deve aceitar X-Forwarded-Proto de cliente que nao e proxy confiavel")
    void naoDeveConfiarNoProtocoloEncaminhadoPorClienteQualquer() {
        var headers = new HttpHeaders();
        headers.set("X-Forwarded-Proto", "https");
        headers.set("X-Forwarded-Host", "evil.example");

        var response = restTemplate.exchange(
                "/actuator/health", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getHeaders().getFirst(HSTS))
                .describedAs("HSTS emitido a partir de header forjado pelo proprio cliente")
                .isNull();
    }
}
