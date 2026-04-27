package com.cartola.odds.model;

import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import lombok.Builder;
import lombok.Getter;

/**
 * Linha do relatorio de pagamento — um profissional escalado e o respectivo custo.
 */
@Getter
@Builder
public class ItemPagamento {

    private final int          atletaId;
    private final String       apelido;
    private final Posicao      posicao;
    private final String       siglaClube;
    private final String       nomeClube;
    private final StatusAtleta status;
    private final double       valorPagamento;
    private final boolean      titular;
    private final boolean      capitao;

    public String getPosicaoLabel() {
        return posicao == null ? "" : posicao.getLabel();
    }

    public String getStatusLabel() {
        return status == null ? "" : status.getLabel();
    }

    /** Funcao no time: Capitao, Titular ou Reserva. */
    public String getFuncao() {
        if (capitao) return "Capitao";
        return titular ? "Titular" : "Reserva";
    }
}
