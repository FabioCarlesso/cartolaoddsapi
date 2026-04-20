package com.cartola.odds.util;

import java.text.Normalizer;

public final class NormalizadorUtil {

    private NormalizadorUtil() {}

    /**
     * Remove acentos, converte para lowercase e elimina caracteres especiais.
     * Usado para cruzamento de nomes entre The Odds API e Cartola FC.
     *
     * Exemplos:
     *   "Atlético-MG"    → "atletico mg"
     *   "São Paulo FC"   → "sao paulo fc"
     *   "Grêmio"         → "gremio"
     */
    public static String normalizar(String nome) {
        if (nome == null || nome.isBlank()) return "";
        var semAcento = Normalizer.normalize(nome.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.replaceAll("[^a-z0-9 ]", "").strip();
    }
}
