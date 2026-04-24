package com.cartola.odds.util;

import java.text.Normalizer;
import java.util.Map;

public final class NormalizadorUtil {

    private NormalizadorUtil() {}

    // Aliases para nomes que diferem entre The Odds API e Cartola FC
    private static final Map<String, String> ALIASES = Map.of(
        "atletico mineiro",     "atletico mg",
        "atletico mineiro mg",  "atletico mg",
        "atletico paranaense",  "athletico pr",
        "athletico paranaense", "athletico pr",
        "vasco da gama",        "vasco",
        "bragantino sp",        "bragantino",
        "red bull bragantino",  "bragantino",
        "atletico goianiense",  "atletico go",
        "america mineiro",      "america mg"
    );

    /**
     * Remove acentos, converte para lowercase, elimina caracteres especiais
     * e aplica aliases para equalizar nomes entre The Odds API e Cartola FC.
     */
    public static String normalizar(String nome) {
        if (nome == null || nome.isBlank()) return "";
        var semAcento = Normalizer.normalize(nome.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        var normalizado = semAcento
                .replace('-', ' ')
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .strip();
        return ALIASES.getOrDefault(normalizado, normalizado);
    }
}
