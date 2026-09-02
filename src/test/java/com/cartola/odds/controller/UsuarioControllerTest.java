package com.cartola.odds.controller;

import com.cartola.odds.exception.ConflitoException;
import com.cartola.odds.exception.RecursoNaoEncontradoException;
import com.cartola.odds.exception.SenhaInvalidaException;
import com.cartola.odds.exception.TentativasExcedidasException;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.model.response.PaginaResponse;
import com.cartola.odds.model.response.UsuarioResponse;
import com.cartola.odds.service.UsuarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsuarioController.class)
// Filtros de seguranca desligados, como nas demais controllers: aqui interessa o
// contrato HTTP. O @WithMockUser popula o SecurityContext direto, entao o @PreAuthorize
// da controller continua sendo exercido — desde que a configuracao abaixo o habilite,
// ja que o slice web nao carrega o SecurityConfig da aplicacao.
@AutoConfigureMockMvc(addFilters = false)
@Import(UsuarioControllerTest.MethodSecurityConfig.class)
@DisplayName("UsuarioController")
class UsuarioControllerTest {

    /**
     * {@code proxyTargetClass = true} reproduz o que a aplicacao faz por padrao
     * (`spring.aop.proxy-target-class`, ligado pelo Spring Boot). Sem isso, o slice web
     * — que nao carrega a auto-configuracao de AOP — proxia a controller pela interface
     * {@code UsuarioApi}, e o proxy JDK resultante perde os mapeamentos: toda rota
     * responderia 404.
     */
    @TestConfiguration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean UsuarioService usuarioService;

    private static final String NOVO_USUARIO = """
            {"nome": "Novo Usuario", "email": "novo@cartolaodds.local", "senha": "senha-forte-123", "perfil": "USER"}
            """;

    private static UsuarioResponse usuario(Long id, String email, Perfil perfil, boolean ativo) {
        return UsuarioResponse.builder()
                .id(id)
                .nome("Novo Usuario")
                .email(email)
                .perfil(perfil)
                .ativo(ativo)
                .criadoEm(LocalDateTime.now())
                .build();
    }

    // ── POST /api/usuarios ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 201 com Location ao criar usuario como ADMIN")
    void deveCriarUsuario() throws Exception {
        when(usuarioService.criar(any())).thenReturn(usuario(7L, "novo@cartolaodds.local", Perfil.USER, true));

        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(NOVO_USUARIO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/usuarios/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("novo@cartolaodds.local"))
                .andExpect(jsonPath("$.perfil").value("USER"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("deve retornar 403 no contrato de erro ao criar usuario como USER")
    void deveNegarCriacaoParaUser() throws Exception {
        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(NOVO_USUARIO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("Acesso negado"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(usuarioService, never()).criar(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 409 quando o e-mail ja existe")
    void deveRetornar409ParaEmailDuplicado() throws Exception {
        when(usuarioService.criar(any()))
                .thenThrow(new ConflitoException("Ja existe um usuario com o e-mail informado."));

        mockMvc.perform(post("/api/usuarios").contentType(MediaType.APPLICATION_JSON).content(NOVO_USUARIO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.erro").value("Conflito"))
                .andExpect(jsonPath("$.mensagem").value("Ja existe um usuario com o e-mail informado."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 400 quando o e-mail e malformado")
    void deveRetornar400ParaEmailInvalido() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Novo", "email": "nao-e-email", "senha": "senha-forte-123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("email deve ser um endereco valido"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 400 quando a senha tem menos de 8 caracteres")
    void deveRetornar400ParaSenhaCurta() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Novo", "email": "novo@cartolaodds.local", "senha": "curta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("senha deve ter entre 8 e 72 caracteres"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 400, e nao 500, para valor fora do enum de perfil")
    void deveRetornar400ParaPerfilInvalido() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Novo", "email": "novo@cartolaodds.local", "senha": "senha-forte-123", "perfil": "SUPERADMIN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("Corpo da requisicao invalido ou mal formatado."))
                // A resposta nao enumera os valores aceitos nem nomeia a classe do enum.
                .andExpect(jsonPath("$.mensagem").value(not(containsString("Perfil"))));

        verify(usuarioService, never()).criar(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 400, e nao 500, para JSON mal formado")
    void deveRetornar400ParaJsonMalFormado() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(usuarioService, never()).criar(any());
    }

    // ── GET /api/usuarios ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve listar usuarios paginados sem expor a senha")
    void deveListarUsuarios() throws Exception {
        when(usuarioService.listar(any())).thenReturn(PaginaResponse.<UsuarioResponse>builder()
                .conteudo(List.of(usuario(1L, "admin@cartolaodds.local", Perfil.ADMIN, true),
                                  usuario(2L, "user@cartolaodds.local", Perfil.USER, false)))
                .pagina(0)
                .tamanho(20)
                .totalElementos(2)
                .totalPaginas(1)
                .ultima(true)
                .build());

        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo.length()").value(2))
                .andExpect(jsonPath("$.conteudo[0].email").value("admin@cartolaodds.local"))
                .andExpect(jsonPath("$.conteudo[0].senha").doesNotExist())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.ultima").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("deve retornar 403 ao listar usuarios como USER")
    void deveNegarListagemParaUser() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // ── GET /api/usuarios/{id} ────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 404 para id inexistente")
    void deveRetornar404ParaIdInexistente() throws Exception {
        when(usuarioService.buscarPorId(99L))
                .thenThrow(new RecursoNaoEncontradoException("Usuario nao encontrado para o id 99."));

        mockMvc.perform(get("/api/usuarios/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.erro").value("Recurso nao encontrado"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve detalhar usuario pelo id")
    void deveDetalharUsuario() throws Exception {
        when(usuarioService.buscarPorId(7L)).thenReturn(usuario(7L, "novo@cartolaodds.local", Perfil.USER, true));

        mockMvc.perform(get("/api/usuarios/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    // ── GET /api/usuarios/me ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "user@cartolaodds.local", roles = "USER")
    @DisplayName("deve devolver os dados da propria conta sem exigir ADMIN")
    void deveDevolverPropriaConta() throws Exception {
        when(usuarioService.buscarLogado()).thenReturn(usuario(2L, "user@cartolaodds.local", Perfil.USER, true));

        mockMvc.perform(get("/api/usuarios/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@cartolaodds.local"))
                .andExpect(jsonPath("$.perfil").value("USER"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    // ── PATCH /api/usuarios/me/senha ──────────────────────────────────

    @Test
    @WithMockUser(username = "user@cartolaodds.local", roles = "USER")
    @DisplayName("deve retornar 200 ao trocar a senha com a senha atual correta")
    void deveTrocarSenha() throws Exception {
        doNothing().when(usuarioService).alterarSenha(any());

        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "senha-forte-123", "novaSenha": "senha-nova-456"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@cartolaodds.local", roles = "USER")
    @DisplayName("deve retornar 422 quando a senha atual nao confere")
    void deveRetornar422ParaSenhaAtualErrada() throws Exception {
        doThrow(new SenhaInvalidaException("A senha atual informada nao confere."))
                .when(usuarioService).alterarSenha(any());

        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "senha-errada", "novaSenha": "senha-nova-456"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.erro").value("Senha invalida"));
    }

    @Test
    @WithMockUser(username = "user@cartolaodds.local", roles = "USER")
    @DisplayName("deve retornar 400 quando a nova senha e curta demais")
    void deveRetornar400ParaNovaSenhaCurta() throws Exception {
        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "senha-forte-123", "novaSenha": "curta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("novaSenha deve ter entre 8 e 72 caracteres"));

        verify(usuarioService, never()).alterarSenha(any());
    }

    @Test
    @WithMockUser(username = "user@cartolaodds.local", roles = "USER")
    @DisplayName("deve retornar 429 quando o freio bloqueia a conferencia da senha atual")
    void deveRetornar429NaTrocaDeSenhaBloqueada() throws Exception {
        doThrow(new TentativasExcedidasException("Muitas tentativas malsucedidas. Tente novamente em ate 5 minutos."))
                .when(usuarioService).alterarSenha(any());

        mockMvc.perform(patch("/api/usuarios/me/senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"senhaAtual": "senha-errada", "novaSenha": "senha-nova-456"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.erro").value("Tentativas excedidas"));
    }

    // ── PATCH /api/usuarios/{id} ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve atualizar usuario como ADMIN")
    void deveAtualizarUsuario() throws Exception {
        when(usuarioService.atualizar(eq(7L), any()))
                .thenReturn(usuario(7L, "novo@cartolaodds.local", Perfil.ADMIN, true));

        mockMvc.perform(patch("/api/usuarios/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"perfil": "ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("ADMIN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 409 ao rebaixar o ultimo administrador ativo")
    void deveRetornar409AoRebaixarUltimoAdmin() throws Exception {
        when(usuarioService.atualizar(eq(1L), any()))
                .thenThrow(new ConflitoException("Nao e possivel rebaixar o perfil do ultimo administrador ativo."));

        mockMvc.perform(patch("/api/usuarios/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"perfil": "USER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value("Nao e possivel rebaixar o perfil do ultimo administrador ativo."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 400 quando o nome vem apenas com espacos")
    void deveRetornar400ParaNomeEmBranco() throws Exception {
        mockMvc.perform(patch("/api/usuarios/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("nome nao pode ser apenas espacos"));

        verify(usuarioService, never()).atualizar(any(), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("deve retornar 403 ao atualizar usuario como USER")
    void deveNegarAtualizacaoParaUser() throws Exception {
        mockMvc.perform(patch("/api/usuarios/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Outro"}
                                """))
                .andExpect(status().isForbidden());

        verify(usuarioService, never()).atualizar(any(), any());
    }

    // ── DELETE /api/usuarios/{id} ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 204 ao desativar usuario")
    void deveDesativarUsuario() throws Exception {
        doNothing().when(usuarioService).desativar(7L);

        mockMvc.perform(delete("/api/usuarios/7")).andExpect(status().isNoContent());

        verify(usuarioService).desativar(7L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("deve retornar 409 ao desativar a propria conta")
    void deveRetornar409AoDesativarPropriaConta() throws Exception {
        doThrow(new ConflitoException("Um administrador nao pode desativar a propria conta."))
                .when(usuarioService).desativar(1L);

        mockMvc.perform(delete("/api/usuarios/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Um administrador nao pode desativar a propria conta."));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("deve retornar 403 ao desativar usuario como USER")
    void deveNegarDesativacaoParaUser() throws Exception {
        mockMvc.perform(delete("/api/usuarios/7")).andExpect(status().isForbidden());

        verify(usuarioService, never()).desativar(any());
    }
}
