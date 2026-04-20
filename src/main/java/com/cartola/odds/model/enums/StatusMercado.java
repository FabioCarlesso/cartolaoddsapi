package com.cartola.odds.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * Status do mercado do Cartola FC conforme retornado pelo endpoint /mercado/status.
 *
 * Valores conhecidos:
 *  1 - Aberto       : mercado aberto para escalacao (durante a semana, antes da rodada)
 *  2 - Fechado      : mercado fechado (durante os jogos da rodada)
 *  3 - Em manutencao: mercado em manutencao ou pre-temporada
 *  4 - Parcial      : mercado parcialmente aberto (alguns jogos ja ocorreram)
 *  6 - Finalizando  : processamento pos-rodada em andamento
 */
@Getter
@RequiredArgsConstructor
public enum StatusMercado {

    ABERTO(1,       "Aberto",       "Mercado aberto para escalacao.",                         false),
    FECHADO(2,      "Fechado",      "Mercado fechado. Rodada em andamento.",                  true),
    MANUTENCAO(3,   "Manutencao",   "Mercado em manutencao ou pre-temporada.",                true),
    PARCIAL(4,      "Parcial",      "Mercado parcialmente aberto. Alguns jogos ja ocorreram.", true),
    FINALIZANDO(6,  "Finalizando",  "Processamento pos-rodada em andamento.",                  true),
    DESCONHECIDO(-1,"Desconhecido", "Status desconhecido. Dados podem estar desatualizados.",  true);

    private final int    codigo;
    private final String label;
    private final String aviso;
    private final boolean exibirAviso;

    public static StatusMercado fromCodigo(int codigo) {
        return Arrays.stream(values())
                .filter(s -> s.codigo == codigo)
                .findFirst()
                .orElse(DESCONHECIDO);
    }

    public boolean isAberto() {
        return this == ABERTO;
    }
}
