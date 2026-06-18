package com.cartola.odds.model;

/**
 * Formacao tatica variavel do time, expressa no formato "def-mei-ata"
 * (ex: "4-3-3") usado pelo Cartola FC, onde o primeiro numero e o total de
 * defensores (laterais + zagueiros). As posicoes fixas (GOL = 1, LAT = 2,
 * TEC = 1) nao variam; os zagueiros sao derivados como {@code defensores - LAT}.
 *
 * <p>A soma das posicoes de linha ({@link #totalLinhas()}) deve ser sempre 10.
 */
public record FormacaoConfig(int defensores, int meias, int atacantes) {

    /**
     * Numero fixo de laterais (LAT) em qualquer formacao. Os zagueiros sao o
     * restante dos defensores ({@code defensores - LATERAIS}), mantendo a
     * derivacao de posicoes alinhada com o {@code GET /api/time}.
     */
    public static final int LATERAIS = 2;

    /** Total de jogadores de linha que variam por formacao (deve ser 10). */
    public int totalLinhas() {
        return defensores + meias + atacantes;
    }

    /** Zagueiros derivados: total de defensores menos os laterais fixos. */
    public int zagueiros() {
        return defensores - LATERAIS;
    }

    /** Laterais da formacao (sempre {@value #LATERAIS}). */
    public int laterais() {
        return LATERAIS;
    }

    /** Rotulo no formato "def-mei-ata" (ex: "4-3-3"). */
    public String label() {
        return "%d-%d-%d".formatted(defensores, meias, atacantes);
    }
}
