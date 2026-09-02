package com.cartola.odds.controller;

import com.cartola.odds.exception.RecursoNaoEncontradoException;
import com.cartola.odds.exception.TentativasExcedidasException;
import com.cartola.odds.model.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Limite de caracteres do valor recebido do cliente ecoado em mensagens de erro. */
    private static final int VALOR_MAX_CHARS = 50;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Argumento invalido: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Parametro invalido", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .reduce((a, b) -> a + "; " + b)
                .orElse("Parametro invalido");

        log.warn("Validacao invalida: {}", mensagem);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Parametro invalido", mensagem));
    }

    /**
     * Valor de query param/path variable que nao converte para o tipo esperado
     * (ex.: {@code ?orcamento=abc}, {@code ?excluirDuvida=abc}). E erro do cliente:
     * sem este handler cairia no generico e responderia 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var tipoEsperado = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconhecido";
        var mensagem = "Parametro '%s' invalido: '%s' nao e um valor valido do tipo %s."
                .formatted(ex.getName(), truncar(ex.getValue()), tipoEsperado);

        log.warn("Parametro com tipo invalido: {}", mensagem);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, "Parametro invalido", mensagem));
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        log.warn("Recurso nao encontrado: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Recurso nao encontrado", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.error("Erro de estado: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422, "Erro no pipeline", ex.getMessage()));
    }

    /**
     * Falha de login vinda do {@code AuthenticationManager}. Mensagem generica de proposito:
     * credencial errada, e-mail inexistente e usuario inativo respondem o mesmo 401, para
     * que o cliente nao consiga enumerar usuarios.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("Falha de autenticacao: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(401, "Credenciais invalidas", "E-mail ou senha invalidos."));
    }

    /**
     * Freio de forca bruta no login. Vem antes do 401 de propósito: a resposta diz que
     * houve excesso de tentativas, nao se a credencial estava certa.
     */
    @ExceptionHandler(TentativasExcedidasException.class)
    public ResponseEntity<ErrorResponse> handleTentativasExcedidas(TentativasExcedidasException ex) {
        log.warn("Tentativas de login excedidas: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(429, "Tentativas excedidas", ex.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClient(RestClientException ex) {
        log.error("Erro ao chamar API externa: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(502, "Erro de comunicacao com API externa", ex.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Recurso nao encontrado: {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "Recurso nao encontrado", ex.getResourcePath()));
    }

    /**
     * Limita o valor recebido do cliente antes de eco-lo na resposta e no log, evitando
     * que uma query string enorme vire corpo de erro e linha de log do mesmo tamanho.
     */
    private String truncar(Object valor) {
        var texto = String.valueOf(valor);
        return texto.length() <= VALOR_MAX_CHARS ? texto : texto.substring(0, VALOR_MAX_CHARS) + "...";
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erro inesperado: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "Erro interno", ex.getMessage()));
    }
}
