package com.cartola.odds.exception;

/**
 * Estado atual do recurso impede a operacao pedida (e-mail ja cadastrado, ultimo
 * administrador ativo, administrador agindo sobre a propria conta).
 * Mapeada para HTTP 409 pelo {@code GlobalExceptionHandler}.
 */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
