package com.cartola.odds.controller;

import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.model.response.LoginResponse;
import com.cartola.odds.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
// Filtros de seguranca desligados: estes testes verificam o contrato da controller,
// e o fluxo real de autenticacao tem cobertura em SegurancaIntegrationTest.
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuthService authService;

    private static final String LOGIN_VALIDO = """
            {"email": "admin@cartolaodds.local", "senha": "senha-forte-123"}
            """;

    @Test
    @DisplayName("deve retornar 200 com accessToken para credenciais validas")
    void deveRetornar200ComToken() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.builder()
                .accessToken("token-jwt")
                .tipo("Bearer")
                .expiraEm(LocalDateTime.now().plusHours(24))
                .nome("Administrador")
                .perfil(Perfil.ADMIN)
                .build());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-jwt"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.perfil").value("ADMIN"))
                .andExpect(jsonPath("$.nome").value("Administrador"));
    }

    @Test
    @DisplayName("deve retornar 401 no contrato de erro para credenciais invalidas")
    void deveRetornar401ParaCredenciaisInvalidas() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("deve retornar o mesmo 401 generico para usuario inativo, sem revelar o motivo")
    void deveRetornar401GenericoParaUsuarioInativo() throws Exception {
        when(authService.login(any())).thenThrow(new DisabledException("User is disabled"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha invalidos."));
    }

    @Test
    @DisplayName("deve retornar 400 quando o e-mail nao e um endereco valido")
    void deveRetornar400ParaEmailInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nao-e-email\", \"senha\": \"senha-forte-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("deve retornar 400 quando a senha vem em branco")
    void deveRetornar400ParaSenhaEmBranco() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"admin@cartolaodds.local\", \"senha\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("senha e obrigatoria"));
    }
}
