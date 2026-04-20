package com.cartola.odds.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum Posicao {

    GOL(1, "Goleiro"),
    LAT(2, "Lateral"),
    ZAG(3, "Zagueiro"),
    MEI(4, "Meia"),
    ATA(5, "Atacante"),
    TEC(6, "Técnico");

    private final int id;
    private final String label;

    public static Optional<Posicao> fromId(int id) {
        return Arrays.stream(values())
                .filter(p -> p.id == id)
                .findFirst();
    }

    public static Optional<Posicao> fromSigla(String sigla) {
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(sigla))
                .findFirst();
    }
}
