package com.cartola.odds.exception;

/**
 * Lancada quando um recurso solicitado nao existe (ex: rodada sem escalacao registrada).
 * Mapeada para HTTP 404 pelo {@code GlobalExceptionHandler}.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
