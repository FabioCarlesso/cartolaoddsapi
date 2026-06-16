package com.cartola.odds.model;

/**
 * Resultado da montagem de um time para uma formacao especifica, usado na
 * comparacao entre multiplas formacoes.
 */
public record ResultadoFormacao(FormacaoConfig formacao, Time time) {
}
