package com.cartola.odds.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum StatusAtleta {

    CONTUNDIDO(2, "Contundido", false),
    SUSPENSO(3, "Suspenso", false),
    NULO(5, "Nulo", false),
    DUVIDA(6, "⚠️ Dúvida", true),
    PROVAVEL(7, "Provável", true);

    private final int id;
    private final String label;
    private final boolean escalavel;

    public static Optional<StatusAtleta> fromId(int id) {
        return Arrays.stream(values())
                .filter(s -> s.id == id)
                .findFirst();
    }

    public static Set<Integer> idsEscalaveis() {
        return Set.of(DUVIDA.id, PROVAVEL.id);
    }
}
