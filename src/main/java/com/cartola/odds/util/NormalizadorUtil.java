package com.cartola.odds.util;

import java.text.Normalizer;
import java.util.Map;

public final class NormalizadorUtil {

    private NormalizadorUtil() {}

    // Aliases para nomes que diferem entre The Odds API, Cartola FC e outras fontes.
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("america futebol clube", "america mg"),
            Map.entry("america fc mg", "america mg"),
            Map.entry("america mineiro", "america mg"),
            Map.entry("athletico paranaense", "athletico pr"),
            Map.entry("atletico paranaense", "athletico pr"),
            Map.entry("atletico goianiense", "atletico go"),
            Map.entry("atletico mineiro", "atletico mg"),
            Map.entry("atletico mineiro mg", "atletico mg"),
            Map.entry("botafogo fr", "botafogo"),
            Map.entry("botafogo rj", "botafogo"),
            Map.entry("bragantino sp", "bragantino"),
            Map.entry("red bull bragantino", "bragantino"),
            Map.entry("corinthians paulista", "corinthians"),
            Map.entry("sport club corinthians paulista", "corinthians"),
            Map.entry("cr vasco da gama", "vasco"),
            Map.entry("cruzeiro ec", "cruzeiro"),
            Map.entry("cruzeiro mg", "cruzeiro"),
            Map.entry("ec bahia", "bahia"),
            Map.entry("ec juventude", "juventude"),
            Map.entry("ec vitoria", "vitoria"),
            Map.entry("fluminense fc", "fluminense"),
            Map.entry("fortaleza ec", "fortaleza"),
            Map.entry("gremio fbpa", "gremio"),
            Map.entry("internacional", "internacional"),
            Map.entry("inter", "internacional"),
            Map.entry("sc internacional", "internacional"),
            Map.entry("mirassol fc", "mirassol"),
            Map.entry("palmeiras sp", "palmeiras"),
            Map.entry("santos fc", "santos"),
            Map.entry("sao paulo fc", "sao paulo"),
            Map.entry("se palmeiras", "palmeiras"),
            Map.entry("sport recife", "sport"),
            Map.entry("sportrecife", "sport"),
            Map.entry("vasco da gama", "vasco"),
            Map.entry("vitoria ba", "vitoria")
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
