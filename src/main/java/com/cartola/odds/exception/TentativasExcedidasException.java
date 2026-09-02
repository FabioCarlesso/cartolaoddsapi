package com.cartola.odds.exception;

/** Excesso de tentativas de login para o mesmo e-mail dentro da janela do freio. */
public class TentativasExcedidasException extends RuntimeException {

    public TentativasExcedidasException(String mensagem) {
        super(mensagem);
    }
}
