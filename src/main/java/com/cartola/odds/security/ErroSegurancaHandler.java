package com.cartola.odds.security;

import com.cartola.odds.model.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Escreve 401 e 403 no mesmo contrato {@link ErrorResponse} usado pelo
 * {@code GlobalExceptionHandler}.
 *
 * <p>Sem isto, esses dois status nascem dentro do filter chain — antes do MVC — e o
 * cliente receberia a pagina de erro padrao do container em vez de JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErroSegurancaHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.debug("Acesso nao autenticado a {}: {}", request.getRequestURI(), authException.getMessage());
        escrever(response, HttpStatus.UNAUTHORIZED,
                "Nao autenticado", "Autenticacao necessaria para acessar este recurso.");
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.debug("Acesso negado a {}: {}", request.getRequestURI(), accessDeniedException.getMessage());
        escrever(response, HttpStatus.FORBIDDEN,
                "Acesso negado", "Voce nao tem permissao para acessar este recurso.");
    }

    private void escrever(HttpServletResponse response, HttpStatus status, String erro, String mensagem)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status.value(), erro, mensagem));
    }
}
