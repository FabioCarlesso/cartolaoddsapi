package com.cartola.odds.actuator;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator na porta unica da aplicacao, atravessando o filter chain de verdade.
 *
 * <p>Antes o Actuator vivia em {@code management.server.port=9090} com bind em
 * {@code 127.0.0.1}, e era o bind — nao uma regra — que o protegia. Numa plataforma que
 * publica uma porta so esse arranjo nao sobrevive, entao quem protege agora e a matriz do
 * {@code SecurityConfig}: {@code health} e {@code info} publicos, para o healthcheck da
 * plataforma consultar sem token, e {@code metrics} e {@code prometheus} so para ADMIN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
@DisplayName("Actuator Endpoints")
class ActuatorEndpointsTest {

    private static final String SENHA = "senha-forte-123";
    private static final String EMAIL_ADMIN = "admin-actuator@cartolaodds.local";
    private static final String EMAIL_USER = "user-actuator@cartolaodds.local";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int porta;

    /**
     * Sem {@code @Transactional}: as chamadas saem por HTTP real, numa thread do Tomcat que
     * nao enxergaria uma transacao aberta pela thread do teste.
     */
    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.save(criar("Administrador", EMAIL_ADMIN, Perfil.ADMIN));
        usuarioRepository.save(criar("Usuario comum", EMAIL_USER, Perfil.USER));
    }

    @Test
    @DisplayName("/actuator/health deve retornar 200 sem token — e o healthcheck da plataforma")
    void actuatorHealthDeveRetornar200SemToken() {
        ResponseEntity<String> response = get("/health", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    @DisplayName("/actuator/health sem token nao deve detalhar componentes")
    void actuatorHealthSemTokenNaoDeveDetalharComponentes() {
        // show-details=when_authorized: o corpo anonimo e so o status agregado. Detalhar
        // aqui entregaria a um anonimo o estado do banco e das dependencias.
        assertThat(get("/health", null).getBody()).doesNotContain("components");
    }

    @Test
    @DisplayName("/actuator/health deve detalhar componentes para ADMIN")
    void actuatorHealthDeveDetalharComponentesParaAdmin() {
        assertThat(get("/health", token(EMAIL_ADMIN)).getBody()).contains("components");
    }

    @Test
    @DisplayName("/actuator/info deve retornar 200 sem token")
    void actuatorInfoDeveRetornar200SemToken() {
        assertThat(get("/info", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/metrics", "/prometheus"})
    @DisplayName("metricas devem exigir token")
    void metricasDevemExigirToken(String endpoint) {
        assertThat(get(endpoint, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/metrics", "/prometheus"})
    @DisplayName("metricas devem responder 403 para USER autenticado")
    void metricasDevemRecusarUser(String endpoint) {
        assertThat(get(endpoint, token(EMAIL_USER)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("/actuator/metrics deve listar nomes de metricas para ADMIN")
    void actuatorMetricsDeveListarNomes() {
        ResponseEntity<String> response = get("/metrics", token(EMAIL_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("names");
    }

    @Test
    @DisplayName("/actuator/metrics/{nome} deve retornar detalhe de uma metrica para ADMIN")
    void actuatorMetricsNomeDeveRetornarDetalhe() {
        ResponseEntity<String> response = get("/metrics/jvm.memory.used", token(EMAIL_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm.memory.used");
    }

    @Test
    @DisplayName("/actuator/prometheus deve retornar conteudo no formato Prometheus para ADMIN")
    void actuatorPrometheusDeveRetornarFormatoPrometheus() {
        ResponseEntity<String> response = get("/prometheus", token(EMAIL_ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("application=\"cartolaoddsapi\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/env", "/beans", "/heapdump"})
    @DisplayName("endpoints sensiveis do Actuator nao devem estar expostos nem para ADMIN")
    void actuatorEndpointsSensiveisNaoDevemEstarExpostos(String endpoint) {
        assertThat(get(endpoint, token(EMAIL_ADMIN)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> get(String endpoint, String token) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(
                url("/actuator" + endpoint), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String token(String email) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var corpo = "{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, SENHA);

        var resposta = restTemplate.postForEntity(
                url("/api/auth/login"), new HttpEntity<>(corpo, headers), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

        try {
            return objectMapper.readTree(resposta.getBody()).get("accessToken").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel autenticar " + email, e);
        }
    }

    private Usuario criar(String nome, String email, Perfil perfil) {
        var usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return usuario;
    }

    private String url(String path) {
        return "http://localhost:" + porta + path;
    }
}
