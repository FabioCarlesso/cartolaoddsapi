package com.cartola.odds.model;

import com.cartola.odds.model.enums.Posicao;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class Time {

    private final int rodada;

    /**
     * Aviso sobre o status do mercado.
     * Preenchido quando o mercado nao esta aberto (fechado, manutencao, etc.).
     * Null quando o mercado esta aberto normalmente.
     */
    private final String avisoMercado;

    private final Map<Posicao, List<Atleta>> titulares;
    private final Map<Posicao, Atleta>       reservas;
    private final Atleta                     capitao;
    private final Atleta                     reservaLuxo;
    private final List<String>               alertasDuvida;
    private final double                     custoTotal;
}
