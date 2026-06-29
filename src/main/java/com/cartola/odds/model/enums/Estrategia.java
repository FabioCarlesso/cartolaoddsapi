package com.cartola.odds.model.enums;

/**
 * Estrategia de montagem do time.
 *
 *  SCORE_MAXIMO : maximiza a soma de score dos titulares. Sem orcamento, escolhe
 *                 o time de maior score absoluto. Com orcamento, maximiza o score
 *                 sujeito ao teto de cartoletas, usando o custo-beneficio
 *                 (score/preco) apenas como criterio de desempate.
 */
public enum Estrategia {
    SCORE_MAXIMO
}
