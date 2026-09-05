package com.cartola.odds.model;

import com.cartola.odds.model.response.OddsResponse;

import java.util.List;

/**
 * Odds e a origem delas: consulta ao vivo a The Odds API ou o snapshot persistido.
 *
 * <p>A origem viaja junto com os dados, e nao num campo do cliente, porque o resultado e
 * cacheado: num acerto de cache o metodo nem chega a executar, e uma flag de instancia
 * descreveria a ultima <em>execucao</em> em vez do que este chamador recebeu. Carregada no
 * proprio valor, a origem continua correta no acerto de cache e sob concorrencia.
 */
public record OddsComOrigem(List<OddsResponse> odds, boolean deSnapshot) {

    public static OddsComOrigem aoVivo(List<OddsResponse> odds) {
        return new OddsComOrigem(odds, false);
    }

    public static OddsComOrigem deSnapshot(List<OddsResponse> odds) {
        return new OddsComOrigem(odds, true);
    }

    /** Sem odds e sem snapshot: o pipeline segue sem filtro de favoritos. */
    public static OddsComOrigem indisponivel() {
        return new OddsComOrigem(List.of(), false);
    }

    public boolean vazio() {
        return odds.isEmpty();
    }
}
