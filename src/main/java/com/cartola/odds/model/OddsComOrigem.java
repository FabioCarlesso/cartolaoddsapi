package com.cartola.odds.model;

import com.cartola.odds.model.response.OddsResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Odds, a origem delas (consulta ao vivo ou snapshot persistido) e o instante em que o
 * provedor de fato as produziu.
 *
 * <p>A origem viaja junto com os dados, e nao num campo do cliente, porque o resultado e
 * cacheado: num acerto de cache o metodo nem chega a executar, e uma flag de instancia
 * descreveria a ultima <em>execucao</em> em vez do que este chamador recebeu. Carregada no
 * proprio valor, a origem continua correta no acerto de cache e sob concorrencia.
 *
 * <p>{@code obtidoEm} e o que impede o cache de dobrar a validade: um snapshot de 50 minutos
 * guardado por mais um TTL cheio serviria odds de quase duas horas. Com o instante de origem
 * no valor, o cache guarda so o tempo que resta.
 */
public record OddsComOrigem(List<OddsResponse> odds, boolean deSnapshot, LocalDateTime obtidoEm) {

    public static OddsComOrigem aoVivo(List<OddsResponse> odds) {
        return new OddsComOrigem(odds, false, LocalDateTime.now());
    }

    public static OddsComOrigem deSnapshot(List<OddsResponse> odds, LocalDateTime obtidoEm) {
        return new OddsComOrigem(odds, true, obtidoEm);
    }

    public static OddsComOrigem deSnapshot(List<OddsResponse> odds) {
        return deSnapshot(odds, LocalDateTime.now());
    }

    /** Sem odds e sem snapshot: o pipeline segue sem filtro de favoritos. */
    public static OddsComOrigem indisponivel() {
        return new OddsComOrigem(List.of(), false, LocalDateTime.now());
    }

    public boolean vazio() {
        return odds.isEmpty();
    }
}
