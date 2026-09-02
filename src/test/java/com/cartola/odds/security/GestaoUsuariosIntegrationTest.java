package com.cartola.odds.security;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gestao de usuarios atravessando o filter chain de verdade: o administrador cria a
 * conta, o novo usuario autentica com ela, e as recusas (perfil insuficiente, conta
 * desativada, token derrubado pela troca de senha) chegam ao cliente no contrato de erro.
 *
 * <p>Usa {@code GET /api/usuarios/me} como endpoint autenticado de referencia — ele so le
 * o banco, sem depender das APIs externas do Cartola e das odds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
@DisplayName("Gestao de usuarios — fluxo autenticado")
class GestaoUsuariosIntegrationTest {

    private static final String SENHA = "senha-forte-123";
    private static final String EMAIL_ADMIN = "admin-gestao@cartolaodds.local";
    private static final String EMAIL_USER = "user-gestao@cartolaodds.local";
    private static final String EMAIL_NOVO = "novo-gestao@cartolaodds.local";

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.save(criar("Administrador", EMAIL_ADMIN, Perfil.ADMIN));
        usuarioRepository.save(criar("Usuario comum", EMAIL_USER, Perfil.USER));
        // Segundo administrador: sem ele, toda operacao sobre o admin acima esbarraria na
        // regra do ultimo administrador ativo, escondendo o comportamento sob teste.
        usuarioRepository.save(criar("Administradora reserva", "admin2-gestao@cartolaodds.local", Perfil.ADMIN));
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

    private String autenticar(String email, String senha) throws Exception {
        var resposta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(resposta).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String corpoNovoUsuario(String email, Perfil perfil) {
        return """
                {"nome": "Novo Usuario", "email": "%s", "senha": "%s", "perfil": "%s"}
                """.formatted(email, SENHA, perfil);
    }

    private long idDe(String email) {
        return usuarioRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    // ── Criacao ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deve criar o usuario como ADMIN e permitir que ele autentique em seguida")
    void deveCriarUsuarioQueConsegueLogar() throws Exception {
        var tokenAdmin = autenticar(EMAIL_ADMIN, SENHA);

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovoUsuario(EMAIL_NOVO, Perfil.USER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL_NOVO))
                .andExpect(jsonPath("$.perfil").value("USER"));

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(autenticar(EMAIL_NOVO, SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_NOVO));
    }

    @Test
    @DisplayName("deve recusar com 403 no contrato de erro a criacao feita por um USER")
    void deveRecusarCriacaoPorUser() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(autenticar(EMAIL_USER, SENHA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovoUsuario(EMAIL_NOVO, Perfil.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Acesso negado"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(usuarioRepository.existsByEmailIgnoreCase(EMAIL_NOVO)).isFalse();
    }

    @Test
    @DisplayName("deve recusar com 401 a criacao sem token")
    void deveRecusarCriacaoSemToken() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovoUsuario(EMAIL_NOVO, Perfil.USER)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("deve recusar com 409 a criacao com e-mail ja cadastrado")
    void deveRecusarEmailDuplicado() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovoUsuario(EMAIL_USER.toUpperCase(), Perfil.USER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("deve responder 404 para id inexistente")
    void deveResponder404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/api/usuarios/999999").header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── Exposicao da senha ────────────────────────────────────────────

    @Test
    @DisplayName("nao deve expor a senha em nenhuma resposta de /api/usuarios, nem em hash")
    void naoDeveExporSenha() throws Exception {
        var tokenAdmin = autenticar(EMAIL_ADMIN, SENHA);

        var criacao = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoNovoUsuario(EMAIL_NOVO, Perfil.USER)))
                .andReturn().getResponse().getContentAsString();

        var listagem = mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var detalhe = mockMvc.perform(get("/api/usuarios/" + idDe(EMAIL_NOVO))
                        .header("Authorization", bearer(tokenAdmin)))
                .andReturn().getResponse().getContentAsString();

        var hash = usuarioRepository.findByEmailIgnoreCase(EMAIL_NOVO).orElseThrow().getSenha();
        assertThat(criacao).doesNotContain("senha").doesNotContain(SENHA).doesNotContain(hash);
        assertThat(listagem).doesNotContain("senha").doesNotContain(SENHA).doesNotContain(hash);
        assertThat(detalhe).doesNotContain("senha").doesNotContain(SENHA).doesNotContain(hash);
    }

    @Test
    @DisplayName("deve listar usuarios no envelope de paginacao da API")
    void deveListarPaginado() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .param("size", "2")
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.ultima").value(false));
    }

    // ── Payload e parametros invalidos ────────────────────────────────

    @Test
    @DisplayName("deve responder 400, e nao 500, para ordenacao por campo inexistente")
    void deveRecusarOrdenacaoDesconhecida() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("sort", "naoExiste")
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(containsString("Campos aceitos")))
                // Nem o nome da entidade interna nem o campo recebido voltam ao cliente.
                .andExpect(jsonPath("$.mensagem").value(not(containsString("Usuario"))))
                .andExpect(jsonPath("$.mensagem").value(not(containsString("naoExiste"))));
    }

    @Test
    @DisplayName("deve responder 400 para ordenacao pela senha")
    void deveRecusarOrdenacaoPorSenha() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("sort", "senha")
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve aceitar ordenacao pelos campos suportados")
    void deveAceitarOrdenacaoSuportada() throws Exception {
        mockMvc.perform(get("/api/usuarios").param("sort", "criadoEm,desc")
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(3));
    }

    @Test
    @DisplayName("deve responder 400, e nao 500, para valor fora do enum de perfil")
    void deveRecusarPerfilInvalido() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "X", "email": "x@cartolaodds.local", "senha": "senha-forte-123", "perfil": "SUPERADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(not(containsString("SUPERADMIN"))));

        assertThat(usuarioRepository.existsByEmailIgnoreCase("x@cartolaodds.local")).isFalse();
    }

    @Test
    @DisplayName("deve responder 400 quando o nome vem apenas com espacos, sem gravar nome vazio")
    void deveRecusarNomeEmBranco() throws Exception {
        mockMvc.perform(patch("/api/usuarios/" + idDe(EMAIL_USER))
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "   "}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(usuarioRepository.findByEmailIgnoreCase(EMAIL_USER).orElseThrow().getNome())
                .isEqualTo("Usuario comum");
    }

    @Test
    @DisplayName("deve responder 429 apos exceder tentativas com a senha atual errada")
    void deveBloquearTrocaDeSenhaAposExcessoDeTentativas() throws Exception {
        // Usuario proprio deste teste: o freio e um bean singleton e seu contador
        // sobrevive ao rollback da transacao, entao queimar as tentativas de um e-mail
        // compartilhado deixaria os demais testes dependendo da ordem de execucao.
        var emailAlvo = "alvo-freio-senha@cartolaodds.local";
        usuarioRepository.save(criar("Alvo do freio", emailAlvo, Perfil.USER));

        var token = autenticar(emailAlvo, SENHA);
        var corpoErrado = """
                {"senhaAtual": "senha-errada", "novaSenha": "senha-nova-456"}
                """;

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(patch("/api/usuarios/me/senha")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoErrado))
                    .andExpect(status().isUnprocessableEntity());
        }

        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoErrado))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.erro").value("Tentativas excedidas"));

        // O freio e compartilhado com o login: o mesmo e-mail ja nao autentica.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(emailAlvo, SENHA)))
                .andExpect(status().isTooManyRequests());

        // E o bloqueio nao respinga em quem nao errou senha nenhuma.
        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", bearer(autenticar(EMAIL_USER, SENHA))))
                .andExpect(status().isOk());
    }

    // ── Propria conta ─────────────────────────────────────────────────

    @Test
    @DisplayName("deve devolver os dados do dono do token em /me sem exigir ADMIN")
    void deveDevolverPropriaConta() throws Exception {
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(autenticar(EMAIL_USER, SENHA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_USER))
                .andExpect(jsonPath("$.perfil").value("USER"));
    }

    @Test
    @DisplayName("deve trocar a senha e derrubar o token usado na propria troca")
    void deveTrocarSenhaEDerrubarToken() throws Exception {
        var token = autenticar(EMAIL_USER, SENHA);

        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "%s", "novaSenha": "senha-nova-456"}
                                """.formatted(SENHA)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/usuarios/me")
                        .header("Authorization", bearer(autenticar(EMAIL_USER, "senha-nova-456"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("deve recusar com 422 a troca de senha com a senha atual errada")
    void deveRecusarSenhaAtualErrada() throws Exception {
        var token = autenticar(EMAIL_USER, SENHA);

        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "senha-errada", "novaSenha": "senha-nova-456"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));

        // O token continua valendo: nada mudou no usuario.
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    // ── Desativacao ───────────────────────────────────────────────────

    @Test
    @DisplayName("deve desativar logicamente: registro mantido, login recusado e token derrubado")
    void deveDesativarLogicamente() throws Exception {
        var tokenUser = autenticar(EMAIL_USER, SENHA);

        mockMvc.perform(delete("/api/usuarios/" + idDe(EMAIL_USER))
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA))))
                .andExpect(status().isNoContent());

        var desativado = usuarioRepository.findByEmailIgnoreCase(EMAIL_USER).orElseThrow();
        assertThat(desativado.isAtivo()).isFalse();
        assertThat(desativado.getTokenVersion()).isEqualTo(1L);

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(tokenUser)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(EMAIL_USER, SENHA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deve recusar com 409 desativar a propria conta de administrador")
    void deveRecusarAutoDesativacao() throws Exception {
        var tokenAdmin = autenticar(EMAIL_ADMIN, SENHA);

        mockMvc.perform(delete("/api/usuarios/" + idDe(EMAIL_ADMIN)).header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(usuarioRepository.findByEmailIgnoreCase(EMAIL_ADMIN).orElseThrow().isAtivo()).isTrue();
    }

    @Test
    @DisplayName("deve recusar com 409 desativar o ultimo administrador ativo, sem alterar o registro")
    void deveRecusarDesativarUltimoAdmin() throws Exception {
        var tokenAdmin = autenticar(EMAIL_ADMIN, SENHA);
        var reservaId = idDe("admin2-gestao@cartolaodds.local");

        // Sobra apenas o administrador logado; ninguem mais pode ser desativado sem
        // deixar a instancia sem acesso administrativo.
        mockMvc.perform(delete("/api/usuarios/" + reservaId).header("Authorization", bearer(tokenAdmin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch("/api/usuarios/" + idDe(EMAIL_ADMIN))
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ativo": false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        var admin = usuarioRepository.findByEmailIgnoreCase(EMAIL_ADMIN).orElseThrow();
        assertThat(admin.isAtivo()).isTrue();
        assertThat(admin.getPerfil()).isEqualTo(Perfil.ADMIN);
    }

    // ── Rebaixamento de perfil ────────────────────────────────────────

    @Test
    @DisplayName("deve derrubar o token do administrador rebaixado a USER")
    void deveDerrubarTokenDoAdminRebaixado() throws Exception {
        var emailReserva = "admin2-gestao@cartolaodds.local";
        var tokenReserva = autenticar(emailReserva, SENHA);

        mockMvc.perform(patch("/api/usuarios/" + idDe(emailReserva))
                        .header("Authorization", bearer(autenticar(EMAIL_ADMIN, SENHA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"perfil": "USER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("USER"));

        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(tokenReserva)))
                .andExpect(status().isUnauthorized());

        // Reautenticado, ele ja nao passa pelas rotas de administrador.
        mockMvc.perform(get("/api/usuarios").header("Authorization", bearer(autenticar(emailReserva, SENHA))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("deve recusar com 409 o rebaixamento do proprio perfil do administrador")
    void deveRecusarAutoRebaixamento() throws Exception {
        var tokenAdmin = autenticar(EMAIL_ADMIN, SENHA);

        mockMvc.perform(patch("/api/usuarios/" + idDe(EMAIL_ADMIN))
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"perfil": "USER"}
                                """))
                .andExpect(status().isConflict());

        assertThat(usuarioRepository.findByEmailIgnoreCase(EMAIL_ADMIN).orElseThrow().getPerfil())
                .isEqualTo(Perfil.ADMIN);
    }
}
