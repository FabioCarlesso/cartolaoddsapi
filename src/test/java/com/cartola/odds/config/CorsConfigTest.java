package com.cartola.odds.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Leitura da lista de origens do CORS, direto no bean — sem contexto Spring, porque cada
 * caso precisa de um valor diferente de {@code app.cors.allowed-origins} e subir um
 * contexto por variacao custaria minutos para testar uma linha.
 */
@DisplayName("CorsConfigurationSource — origens por ambiente")
class CorsConfigTest {

    private static final String QUALQUER_ROTA = "/api/config";

    private final SecurityConfig securityConfig = new SecurityConfig();

    private CorsConfiguration configuracaoPara(String origens) {
        var request = new MockHttpServletRequest("GET", QUALQUER_ROTA);
        return securityConfig.corsConfigurationSource(origens).getCorsConfiguration(request);
    }

    @Test
    @DisplayName("deve aceitar uma origem unica")
    void deveAceitarOrigemUnica() {
        assertThat(configuracaoPara("http://localhost:4200").getAllowedOrigins())
                .containsExactly("http://localhost:4200");
    }

    @Test
    @DisplayName("deve aparar o espaco depois da virgula, que nunca casaria com o header Origin")
    void deveAparaEspacoEntreOrigens() {
        // O browser envia "Origin: http://b.example" sem espaco; sem o trim, a entrada
        // " http://b.example" jamais casaria e o frontend levaria 403 sem explicacao.
        assertThat(configuracaoPara("http://a.example, http://b.example").getAllowedOrigins())
                .containsExactly("http://a.example", "http://b.example");
    }

    @Test
    @DisplayName("deve descartar entrada vazia de virgula sobrando, em vez de liberar a origem vazia")
    void deveDescartarEntradaVazia() {
        assertThat(configuracaoPara("http://a.example,,  ,http://b.example").getAllowedOrigins())
                .containsExactly("http://a.example", "http://b.example");
    }

    @Test
    @DisplayName("nunca deve liberar curinga: o token viaja em header e '*' entregaria a API a qualquer site")
    void nuncaDeveLiberarCuringa() {
        assertThat(configuracaoPara("http://localhost:4200").getAllowedOrigins())
                .doesNotContain("*");
    }

    @Test
    @DisplayName("deve listar HEAD junto de GET — HEAD nao e derivado de GET no preflight")
    void deveListarHeadEntreOsMetodos() {
        assertThat(configuracaoPara("http://localhost:4200").getAllowedMethods())
                .containsExactly("GET", "HEAD", "POST", "PATCH", "DELETE", "OPTIONS");
    }

    @Test
    @DisplayName("deve listar os headers um a um, sem curinga")
    void deveListarHeadersExplicitos() {
        assertThat(configuracaoPara("http://localhost:4200").getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Accept");
    }
}
