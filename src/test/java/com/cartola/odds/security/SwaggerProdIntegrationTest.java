package com.cartola.odds.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fechamento da documentacao no perfil {@code prod}.
 *
 * <p>O contrato completo da API — rotas, parametros, formato de cada corpo — e o mapa que
 * um atacante levaria tempo montando na mao, e o "Try it out" do Swagger UI deixa disparar
 * as chamadas dali mesmo. Em producao o springdoc sai desligado
 * ({@code application-prod.properties}), e por isso o esperado aqui e <strong>404</strong>
 * e nao 401: um 401 confirmaria que a documentacao esta la, atras de uma senha.
 *
 * <p>O contraponto — as mesmas rotas acessiveis sem token no perfil default — esta no
 * {@link PoliticaAcessoIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
@DisplayName("Seguranca — documentacao fechada no perfil prod")
class SwaggerProdIntegrationTest {

    @Autowired MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config"
    })
    @DisplayName("deve responder 404 na documentacao, sem revelar que ela existe")
    void deveResponder404NaDocumentacao(String rota) throws Exception {
        mockMvc.perform(get(rota)).andExpect(status().isNotFound());
    }
}
