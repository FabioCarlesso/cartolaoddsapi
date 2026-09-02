package com.cartola.odds.exception;

/**
 * Senha atual informada na troca de senha nao confere.
 *
 * <p>Mapeada para HTTP 422, e nao 401: quem chama ja esta autenticado — o token e valido,
 * o que falhou foi a confirmacao da senha no corpo. Um 401 aqui faria o cliente achar que
 * a sessao caiu e mandaria o usuario de volta para o login.
 */
public class SenhaInvalidaException extends RuntimeException {

    public SenhaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
