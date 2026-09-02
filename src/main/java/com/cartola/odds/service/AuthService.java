package com.cartola.odds.service;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.request.LoginRequest;
import com.cartola.odds.model.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * Valida as credenciais e emite o access token.
     *
     * <p>Credencial errada, e-mail inexistente e usuario inativo sobem como
     * {@code AuthenticationException} e viram o mesmo 401 generico no
     * {@code GlobalExceptionHandler} — o cliente nao consegue distinguir os tres casos
     * e, com isso, nao consegue enumerar usuarios.
     */
    public LoginResponse login(LoginRequest request) {
        var autenticacao = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha()));

        var usuario = (Usuario) autenticacao.getPrincipal();
        log.info("Login efetuado: {} ({})", usuario.getEmail(), usuario.getPerfil());

        return LoginResponse.builder()
                .accessToken(jwtService.gerarToken(usuario))
                .tipo("Bearer")
                .expiraEm(LocalDateTime.now().plusNanos(jwtService.getExpirationMs() * 1_000_000L))
                .nome(usuario.getNome())
                .perfil(usuario.getPerfil())
                .build();
    }
}
