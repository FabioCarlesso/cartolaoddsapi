package com.cartola.odds.model;

/**
 * Formacao tatica variavel do time, expressa pelas posicoes que mudam entre
 * formacoes do Cartola FC: zagueiros, meias e atacantes. As posicoes fixas
 * (GOL = 1, LAT = 2, TEC = 1) nao variam e por isso ficam fora deste record.
 *
 * <p>A soma das posicoes de linha ({@link #totalLinhas()}) deve ser sempre 10.
 */
public record FormacaoConfig(int zagueiros, int meias, int atacantes) {

    /** Total de jogadores de linha que variam por formacao (deve ser 10). */
    public int totalLinhas() {
        return zagueiros + meias + atacantes;
    }

    /** Rotulo no formato "zag-mei-ata" (ex: "4-3-3"). */
    public String label() {
        return "%d-%d-%d".formatted(zagueiros, meias, atacantes);
    }
}
