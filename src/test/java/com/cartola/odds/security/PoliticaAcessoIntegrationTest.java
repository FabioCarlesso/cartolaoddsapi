package com.cartola.odds.security;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
import com.cartola.odds.service.EscalacaoService;
import com.cartola.odds.service.OddsService;
import com.cartola.odds.service.PipelineService;
import com.cartola.odds.service.JwtService;
import com.cartola.odds.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
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

    /**
     * Nenhum caso aqui confere senha; o campo so nao pode ser nulo na entidade. Ainda assim
     * sao os 60 caracteres de um BCrypt de verdade — {@code $2a$}, custo, 22 de salt e 31 de
     * digest, todos no alfabeto que o algoritmo aceita —, para que um caso futuro que resolva
     * chamar {@code matches()} receba um "nao confere" em vez de um erro de parsing.
     */
    private static final String HASH_IRRELEVANTE = "$2a$10$naoUsadoPorNenhumCasoDesteTeste......................";
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
    @Autowired JwtService jwtService;

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
                // So a escrita de /api/historico subiu para ADMIN: o matcher cita o verbo
                // POST, e esta linha e o que impede que ele vaze para o GET da mesma rota.
                // O 405 que o MVC devolve aqui ja significa "a politica deixou entrar".
                new Caso(HttpMethod.GET, "/api/historico/1/atualizar-pontuacao", Acesso.AUTENTICADO),
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
                // Regrava a pontuacao real de todos os atletas da rodada: escrita na tabela
                // da instancia, precedida de uma chamada a API do Cartola.
                new Caso(HttpMethod.POST, "/api/historico/1/atualizar-pontuacao", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/api/usuarios", Acesso.ADMIN),
                new Caso(HttpMethod.POST, "/api/usuarios", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/api/usuarios/1", Acesso.ADMIN),
                new Caso(HttpMethod.PATCH, "/api/usuarios/1", Acesso.ADMIN),
                new Caso(HttpMethod.DELETE, "/api/usuarios/1", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/actuator/metrics", Acesso.ADMIN),
                new Caso(HttpMethod.GET, "/actuator/prometheus", Acesso.ADMIN),
                // Saldo e consumo de cota da The Odds API: informacao operacional interna (#40).
                new Caso(HttpMethod.GET, "/api/odds/cota", Acesso.ADMIN));
    }

    private String tokenUser;
    private String tokenAdmin;

    /**
     * Os tokens sao emitidos direto pelo {@link JwtService}, sem passar por
     * {@code POST /api/auth/login}. O JUnit instancia a classe uma vez por metodo de teste,
     * entao qualquer login aqui se multiplicaria pelas dezenas de linhas da matriz — e cada
     * um custa uma verificacao BCrypt, que e cara de proposito. O que esta sob teste e a
     * autorizacao, nao a troca de senha por token: o fluxo de login tem cobertura propria no
     * {@code SegurancaIntegrationTest}.
     *
     * <p>Pelo mesmo motivo a senha gravada e um hash fixo, e nao {@code encode(SENHA)}:
     * ninguem confere senha neste teste, e gerar dois hashes BCrypt por metodo so somaria
     * tempo de suite.
     */
    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        var admin = usuarioRepository.save(criar("Administrador", EMAIL_ADMIN, Perfil.ADMIN));
        var user = usuarioRepository.save(criar("Usuario comum", EMAIL_USER, Perfil.USER));
        tokenAdmin = jwtService.gerarToken(admin);
        tokenUser = jwtService.gerarToken(user);
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
        var status = executar(caso, tokenUser);

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
        assertThat(executar(caso, tokenAdmin))
                .describedAs("rota recusada para ADMIN")
                .isNotIn(401, 403);
    }

    /**
     * As linhas {@code PUBLICO} da matriz afirmam so o veredito da autorizacao, entao uma
     * documentacao que parasse de existir no perfil default passaria despercebida por la —
     * 404 nao e 401 nem 403. Aqui o status exato e cobrado.
     */
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "/actuator/health, 200",
            "/actuator/info, 200",
            "/v3/api-docs, 200",
            "/v3/api-docs/swagger-config, 200",
            "/swagger-ui/index.html, 200",
            // O springdoc redireciona /swagger-ui.html para /swagger-ui/index.html
            "/swagger-ui.html, 302"
    })
    @DisplayName("rotas publicas devem responder de fato, e nao apenas escapar do 401")
    void rotasPublicasDevemResponder(String rota, int statusEsperado) throws Exception {
        mockMvc.perform(get(rota)).andExpect(status().is(statusEsperado));
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
                        .header("Authorization", "Bearer " + tokenUser))
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

    /**
     * O contraponto — o HSTS aparecendo quando o proxy confiavel diz que a requisicao veio
     * por TLS — vive no {@link ProxyConfiavelIntegrationTest}, e nao aqui: quem le os
     * {@code X-Forwarded-*} e o {@code RemoteIpValve}, um valve do Tomcat, que o MockMvc nao
     * atravessa. Testar isso por aqui daria um verde que nao significa nada.
     */
    @Test
    @DisplayName("nao deve mandar HSTS em requisicao HTTP — travaria o host do desenvolvedor em HTTPS")
    void naoDeveEnviarHstsEmHttp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
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
        usuario.setSenha(HASH_IRRELEVANTE);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return usuario;
    }
}
