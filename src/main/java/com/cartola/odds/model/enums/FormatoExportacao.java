package com.cartola.odds.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum FormatoExportacao {

    PDF("pdf", "application/pdf"),
    EXCEL("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final String extensao;
    private final String mimeType;

    public static Optional<FormatoExportacao> fromString(String valor) {
        if (valor == null || valor.isBlank()) return Optional.empty();
        var normalizado = valor.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(f -> f.name().equals(normalizado))
                .findFirst();
    }
}
