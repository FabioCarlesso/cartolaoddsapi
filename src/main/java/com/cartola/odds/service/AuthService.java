package com.cartola.odds.service;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.request.LoginRequest;
import com.cartola.odds.model.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginThrottle loginThrottle;

    /**
     * Valida as credenciais e emite o access token.
     *
     * <p>Credencial errada, e-mail inexistente e usuario inativo sobem como
     * {@code AuthenticationException} e viram o mesmo 401 generico no
     * {@code GlobalExceptionHandler} — o cliente nao consegue distinguir os tres casos
     * e, com isso, nao consegue enumerar usuarios.
     */
    public LoginResponse login(LoginRequest request) {
        loginThrottle.verificar(request.getEmail());

        Usuario usuario;
        try {
            var autenticacao = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha()));
            usuario = (Usuario) autenticacao.getPrincipal();
        } catch (AuthenticationException e) {
            loginThrottle.registrarFalha(request.getEmail());
            throw e;
        }

        loginThrottle.registrarSucesso(request.getEmail());
        // Identifica por id, nunca por e-mail: esta linha sai a cada login e o log da
        // plataforma e retido por semanas — o id resolve o mesmo suporte sem virar um
        // deposito de dado pessoal.
        log.info("Login efetuado: id={} ({})", usuario.getId(), usuario.getPerfil());

        return LoginResponse.builder()
                .accessToken(jwtService.gerarToken(usuario))
                .tipo("Bearer")
                .expiraEmSegundos(jwtService.getExpirationSegundos())
                .nome(usuario.getNome())
                .perfil(usuario.getPerfil())
                .build();
    }
}
