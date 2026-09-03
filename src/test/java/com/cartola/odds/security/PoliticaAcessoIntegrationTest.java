package com.cartola.odds.security;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
import com.cartola.odds.service.EscalacaoService;
import com.cartola.odds.service.OddsService;
import com.cartola.odds.service.PipelineService;
import com.cartola.odds.service.RankingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de acesso por rota, verificada nos tres estados que importam: sem token,
 * autenticado como {@code USER} e autenticado como {@code ADMIN}.
 *
 * <p>Cada caso afirma apenas o <em>veredito da autorizacao</em> — passou ou foi recusado —,
 * nunca o status de negocio do endpoint. Um {@code PATCH /api/config} sem corpo responde
 * 400 e um {@code GET} de rota inexistente responde 404; os dois significam a mesma coisa
 * aqui: a politica deixou entrar. Amarrar o teste ao status exato faria a matriz quebrar a
 * cada mudanca de validacao, que e assunto de outro teste.
 *
 * <p>Os servicos que falam com as APIs externas do Cartola e das odds sao substituidos por
 * mocks: a decisao de autorizacao acontece no filter chain, antes de qualquer um deles, e
 * sem os mocks a suite dependeria de rede.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
@DisplayName("Seguranca — matriz de acesso por rota")
class PoliticaAcessoIntegrationTest {

    private static final String SENHA = "senha-forte-123";
    private static final String EMAIL_ADMIN = "admin-matriz@cartolaodds.local";
    private static final String EMAIL_USER = "user-matriz@cartolaodds.local";

    /** Nivel de acesso exigido por uma rota. */
    private enum Acesso { PUBLICO, AUTENTICADO, ADMIN }

    /** Uma linha da matriz: metodo, rota e quem pode chamar. */
    private record Caso(HttpMethod metodo, String rota, Acesso acesso) {
        @Override
        public String toString() {
            return "%s %s → %s".formatted(metodo, rota, acesso);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PipelineService pipelineService;
    @MockitoBean RankingService rankingService;
    @MockitoBean OddsService oddsService;
    @MockitoBean EscalacaoService escalacaoService;

    static Stream<Caso> matriz() {
        return Stream.of(
                // Publico — login e o que a plataforma precisa consultar sem token
                new Caso(HttpMethod.POST, "/api/auth/login", Acesso.PUBLICO),
                new Caso(HttpMethod.GET, "/actuator/health", Acesso.PUBLICO),
                new Caso(HttpMethod.GET, "/actuator/info", Acesso.PUBLICO),
                // Publico fora de producao; no perfil prod estas duas respondem 404
                // (ver SwaggerProdIntegrationTest)
                new Caso(HttpMethod.GET, "/v3/api-docs", Acesso.PUBLICO),
                new Caso(HttpMethod.GET, "/swagger-ui/index.html", Acesso.PUBLICO),

                // Autenticado — consulta, nao muda nada nem gasta cota alem do cache
                new Caso(HttpMethod.GET, "/api/time", Acesso.AUTENTICADO),
                new Caso(HttpMethod.GET, "/api/time/comparar", Acesso.AUTENTICADO),
                new Caso(HttpMethod.GET, "/api/ranking", Acesso.AUTENTICADO),
                new Caso(HttpMethod.GET, "/api/favoritos", Acesso.AUTENTICADO),
                new Caso(HttpMethod.GET, "/api/historico", Acesso.AUTENTICADO),
                new Caso(HttpMethod.GET, "/api/historico/1", Acesso.AUTENTICADO),
                new Caso(HttpMethod.GET, "/api/config", Acesso.AUTENTICADO),
                // HEAD nao herda a autorizacao de GET no Spring Security: um matcher por
                // metodo cobre so aquele metodo. Como as regras de ADMIN aqui citam apenas
                // verbos de escrita, HEAD cai na regra final e segue exigindo token.
                new Caso(HttpMethod.HEAD, "/api/config", Acesso.AUTENTICADO),
                new Caso(HttpMethod.GET, "/api/usuarios/me", Acesso.AUTENTICADO),
                new Caso(HttpMethod.PATCH, "/api/usuarios/me/senha", Acesso.AUTENTICADO),
                // Regra final: rota nao prevista nasce fechada, nao aberta
                new Caso(HttpMethod.GET, "/api/rota-que-nao-existe", Acesso.AUTENTICADO),

                // ADMIN — muda a instancia inteira ou gasta cota paga
                new Caso(HttpMethod.PATCH, "/api/config", Acesso.ADMIN),
                new Caso(HttpMethod.POST, "/api/config/reset", Acesso.ADMIN),
                new Caso(HttpMethod.DELETE, "/api/cache", Acesso.ADMIN),
                new Caso(HttpMethod.DELETE, "/api/cache/atletas", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/api/usuarios", Acesso.ADMIN),
                new Caso(HttpMethod.POST, "/api/usuarios", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/api/usuarios/1", Acesso.ADMIN),
                new Caso(HttpMethod.PATCH, "/api/usuarios/1", Acesso.ADMIN),
                new Caso(HttpMethod.DELETE, "/api/usuarios/1", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/actuator/metrics", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/actuator/prometheus", Acesso.ADMIN));
    }

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.save(criar("Administrador", EMAIL_ADMIN, Perfil.ADMIN));
        usuarioRepository.save(criar("Usuario comum", EMAIL_USER, Perfil.USER));
    }

    @ParameterizedTest(name = "sem token: {0}")
    @MethodSource("matriz")
    @DisplayName("sem token, so as rotas publicas passam")
    void semToken(Caso caso) throws Exception {
        var status = executar(caso, null);

        if (caso.acesso() == Acesso.PUBLICO) {
            assertThat(status).describedAs("rota publica recusada").isNotIn(401, 403);
        } else {
            assertThat(status).describedAs("rota fechada respondeu sem exigir token").isEqualTo(401);
        }
    }

    @ParameterizedTest(name = "USER: {0}")
    @MethodSource("matriz")
    @DisplayName("autenticado como USER, tudo passa menos o que e de ADMIN")
    void comoUser(Caso caso) throws Exception {
        var status = executar(caso, autenticar(EMAIL_USER));

        if (caso.acesso() == Acesso.ADMIN) {
            assertThat(status).describedAs("operacao de administrador liberada para USER").isEqualTo(403);
        } else {
            assertThat(status).describedAs("rota de usuario recusada para USER").isNotIn(401, 403);
        }
    }

    @ParameterizedTest(name = "ADMIN: {0}")
    @MethodSource("matriz")
    @DisplayName("autenticado como ADMIN, toda a matriz passa")
    void comoAdmin(Caso caso) throws Exception {
        assertThat(executar(caso, autenticar(EMAIL_ADMIN)))
                .describedAs("rota recusada para ADMIN")
                .isNotIn(401, 403);
    }

    @Test
    @DisplayName("deve devolver 401 no contrato ErrorResponse, em JSON")
    void deveDevolver401NoContratoDeErro() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Nao autenticado"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("deve devolver 403 no contrato ErrorResponse, em JSON")
    void deveDevolver403NoContratoDeErro() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/cache")
                        .header("Authorization", "Bearer " + autenticar(EMAIL_USER)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Acesso negado"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("deve marcar a resposta com os cabecalhos de defesa do navegador")
    void deveEnviarCabecalhosDeSeguranca() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("nao deve mandar HSTS em requisicao HTTP — travaria o host do desenvolvedor em HTTPS")
    void naoDeveEnviarHstsEmHttp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("deve mandar HSTS quando o proxy sinaliza X-Forwarded-Proto: https")
    void deveEnviarHstsAtrasDeProxyHttps() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Forwarded-Proto", "https"))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("max-age=31536000")))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("includeSubDomains")));
    }

    private int executar(Caso caso, String token) throws Exception {
        var requisicao = requisicao(caso);
        if (token != null) {
            requisicao.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(requisicao).andReturn().getResponse().getStatus();
    }

    /**
     * Corpo vazio de proposito nos verbos de escrita: o que se mede aqui e a autorizacao,
     * que acontece antes da desserializacao. Um 400 de validacao ja significa "passou".
     */
    private MockHttpServletRequestBuilder requisicao(Caso caso) {
        return MockMvcRequestBuilders.request(caso.metodo(), caso.rota())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
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

    private String autenticar(String email) throws Exception {
        var resposta = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, SENHA)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(resposta).get("accessToken").asText();
    }
}
