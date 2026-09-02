package com.cartola.odds.security;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.service.JwtService;
import com.cartola.odds.service.UsuarioDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica a requisicao a partir do header {@code Authorization: Bearer <token>}.
 *
 * <p>Token ausente ou invalido nao interrompe a cadeia: o contexto fica sem autenticacao
 * e quem responde 401 e o {@link ErroSegurancaHandler}, ja com o corpo JSON padrao.
 *
 * <p>Nao e um {@code @Component} de proposito: como bean, o Spring Boot o registraria
 * tambem na cadeia de filtros do servlet, alem da cadeia do Spring Security, e ele
 * rodaria duas vezes por requisicao. Quem o instancia e o {@code SecurityConfig}.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIXO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioDetailsService usuarioDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIXO)) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = header.substring(PREFIXO.length());
        try {
            autenticar(token, request);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            log.debug("Token JWT recusado: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(String token, HttpServletRequest request) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        var email = jwtService.extrairEmail(token);
        var tokenVersion = jwtService.extrairTokenVersion(token);
        if (email == null || tokenVersion == null) {
            log.debug("Token sem subject ou sem claim tokenVersion.");
            return;
        }

        Usuario usuario = usuarioDetailsService.loadUserByUsername(email);
        if (!usuario.isEnabled()) {
            log.debug("Token de usuario inativo: {}", email);
            return;
        }
        // Token emitido antes de uma troca de senha ou desativacao carrega uma versao
        // anterior a do banco e deixa de valer a partir daqui.
        if (usuario.getTokenVersion() != tokenVersion) {
            log.debug("Token com tokenVersion desatualizada para {}.", email);
            return;
        }

        var authentication =
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
