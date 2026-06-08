package com.cartola.odds.model.enums;

/**
 * Estrategia de montagem do time.
 *
 *  SCORE_MAXIMO    : ordena candidatos por score; usada quando nenhum orcamento e informado.
 *  CUSTO_BENEFICIO : ordena candidatos por score/preco para respeitar o orcamento informado,
 *                    priorizando atletas que entregam mais pontos por cartoleta gasta.
 */
public enum Estrategia {
    SCORE_MAXIMO,
    CUSTO_BENEFICIO
}
