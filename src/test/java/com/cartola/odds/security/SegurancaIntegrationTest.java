package com.cartola.odds.security;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
import com.cartola.odds.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo real de autenticacao atravessando o filter chain: login, uso do token e as
 * formas de recusa (sem token, assinatura invalida, tokenVersion vencida, usuario
 * inativo).
 *
 * <p>Usa {@code GET /api/config}, que le apenas o banco, para nao depender das APIs
 * externas do Cartola e das odds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
@DisplayName("Seguranca — autenticacao JWT")
class SegurancaIntegrationTest {

    private static final String SENHA = "senha-forte-123";
    private static final String EMAIL_ATIVO = "ativo@cartolaodds.local";
    private static final String EMAIL_INATIVO = "inativo@cartolaodds.local";

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.save(criar(EMAIL_ATIVO, Perfil.ADMIN, true));
        usuarioRepository.save(criar(EMAIL_INATIVO, Perfil.USER, false));
    }

    private Usuario criar(String email, Perfil perfil, boolean ativo) {
        var usuario = new Usuario();
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
        usuario.setAtivo(ativo);
        return usuario;
    }

    private String corpoLogin(String email, String senha) {
        return "{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha);
    }

    private String autenticar() throws Exception {
        var resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL_ATIVO, SENHA)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(resposta).get("accessToken").asText();
    }

    @Test
    @DisplayName("deve recusar com 401 em JSON quem chama a API sem token")
    void deveRecusarSemToken() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Nao autenticado"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("deve recusar com 401 tambem o endpoint de montagem do time")
    void deveRecusarTimeSemToken() throws Exception {
        mockMvc.perform(get("/api/time")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve autenticar e devolver accessToken com nome e perfil")
    void deveAutenticar() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL_ATIVO, SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                // Duracao, e nao instante: o container roda em UTC e um horario sem fuso
                // seria lido como local pelo cliente.
                .andExpect(jsonPath("$.expiraEmSegundos").value(3600));
    }

    @Test
    @DisplayName("deve aceitar a requisicao autenticada com token valido")
    void deveAceitarComTokenValido() throws Exception {
        mockMvc.perform(get("/api/config").header("Authorization", "Bearer " + autenticar()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deve recusar com 401 generico quando a senha esta errada")
    void deveRecusarSenhaErrada() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL_ATIVO, "senha-errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos."));
    }

    @Test
    @DisplayName("deve responder o mesmo 401 para e-mail inexistente, sem permitir enumeracao")
    void deveRecusarEmailInexistente() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin("ninguem@cartolaodds.local", SENHA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos."));
    }

    @Test
    @DisplayName("deve recusar login de usuario inativo mesmo com a senha correta")
    void deveRecusarUsuarioInativo() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL_INATIVO, SENHA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve recusar token assinado com outra chave")
    void deveRecusarTokenDeOutraChave() throws Exception {
        var tokenForjado = "eyJhbGciOiJIUzI1NiJ9"
                + ".eyJzdWIiOiJhdGl2b0BjYXJ0b2xhb2Rkcy5sb2NhbCIsInRva2VuVmVyc2lvbiI6MH0"
                + ".assinatura-invalida";

        mockMvc.perform(get("/api/config").header("Authorization", "Bearer " + tokenForjado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve recusar token cuja tokenVersion nao e mais a do banco")
    void deveRecusarTokenComVersaoAntiga() throws Exception {
        var token = autenticar();

        var usuario = usuarioRepository.findByEmailIgnoreCase(EMAIL_ATIVO).orElseThrow();
        usuario.incrementarTokenVersion();
        usuarioRepository.saveAndFlush(usuario);

        mockMvc.perform(get("/api/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve recusar token de usuario desativado depois da emissao")
    void deveRecusarTokenDeUsuarioDesativado() throws Exception {
        var token = autenticar();

        var usuario = usuarioRepository.findByEmailIgnoreCase(EMAIL_ATIVO).orElseThrow();
        usuario.setAtivo(false);
        usuarioRepository.saveAndFlush(usuario);

        mockMvc.perform(get("/api/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve emitir token com a tokenVersion atual do usuario")
    void deveEmitirTokenComTokenVersionAtual() throws Exception {
        assertThat(jwtService.extrairTokenVersion(autenticar())).isZero();
    }

    @Test
    @DisplayName("deve persistir a senha com hash BCrypt, nunca em claro")
    void devePersistirSenhaComHash() {
        var usuario = usuarioRepository.findByEmailIgnoreCase(EMAIL_ATIVO).orElseThrow();

        assertThat(usuario.getSenha()).isNotEqualTo(SENHA).startsWith("$2");
        assertThat(passwordEncoder.matches(SENHA, usuario.getSenha())).isTrue();
    }

    @Test
    @DisplayName("nao deve devolver a senha no corpo do login")
    void naoDeveDevolverSenhaNoLogin() throws Exception {
        var resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL_ATIVO, SENHA)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(resposta).doesNotContain(SENHA).doesNotContain("senha");
    }

    @Test
    @DisplayName("deve manter a documentacao OpenAPI acessivel sem token")
    void deveManterOpenApiPublico() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("deve liberar o preflight CORS da origem configurada, que nao carrega token")
    void deveLiberarPreflightCors() throws Exception {
        mockMvc.perform(options("/api/config")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    @DisplayName("deve recusar o preflight de origem nao configurada")
    void deveRecusarPreflightDeOutraOrigem() throws Exception {
        mockMvc.perform(options("/api/config")
                        .header("Origin", "https://site-malicioso.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deve responder 429 apos o limite de tentativas malsucedidas no mesmo e-mail")
    void deveBloquearAposExcessoDeTentativas() throws Exception {
        // E-mail proprio deste teste: o freio e um bean singleton, e sujar o e-mail
        // compartilhado deixaria os demais testes dependendo da ordem de execucao.
        var alvo = "alvo-do-freio@cartolaodds.local";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin(alvo, "tentativa-errada")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(alvo, "tentativa-errada")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.erro").value("Tentativas excedidas"));
    }

    @Test
    @DisplayName("deve manter o login de outro usuario liberado enquanto um e-mail esta bloqueado")
    void naoDeveBloquearOutrosUsuarios() throws Exception {
        var alvo = "outro-alvo-do-freio@cartolaodds.local";
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoLogin(alvo, "tentativa-errada")));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoLogin(EMAIL_ATIVO, SENHA)))
                .andExpect(status().isOk());
    }
}
