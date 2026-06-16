package com.cartola.odds.util;

import com.cartola.odds.model.FormacaoConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Faz o parsing e a validacao de formacoes informadas como texto.
 *
 * <p>Cada formacao e expressa como "zag-mei-ata" (ex: "4-3-3") e a soma das
 * tres posicoes de linha deve ser exatamente {@value #TOTAL_LINHAS}. A lista
 * informada deve conter entre {@value #MIN_FORMACOES} e {@value #MAX_FORMACOES}
 * formacoes distintas; duplicatas sao ignoradas silenciosamente.
 */
public final class FormacaoParser {

    public static final int TOTAL_LINHAS = 10;
    public static final int MIN_FORMACOES = 2;
    public static final int MAX_FORMACOES = 5;

    private FormacaoParser() {
    }

    /**
     * Faz o parsing de uma unica formacao no formato "zag-mei-ata".
     *
     * @throws IllegalArgumentException quando o formato e invalido ou a soma das
     *                                  posicoes de linha e diferente de {@value #TOTAL_LINHAS}
     */
    public static FormacaoConfig parse(String formacao) {
        if (formacao == null || formacao.isBlank()) {
            throw new IllegalArgumentException("Formacao nao pode ser vazia.");
        }

        String[] partes = formacao.trim().split("-");
        if (partes.length != 3) {
            throw new IllegalArgumentException(
                    "Formacao invalida: '" + formacao.trim()
                            + "'. Use o formato zag-mei-ata (ex: 4-3-3).");
        }

        int zag = parseNumero(partes[0], formacao);
        int mei = parseNumero(partes[1], formacao);
        int ata = parseNumero(partes[2], formacao);

        FormacaoConfig config = new FormacaoConfig(zag, mei, ata);
        if (config.totalLinhas() != TOTAL_LINHAS) {
            throw new IllegalArgumentException(
                    "Formacao invalida: '" + formacao.trim() + "'. A soma das posicoes de linha "
                            + "(zag + mei + ata) deve ser " + TOTAL_LINHAS
                            + ", mas foi " + config.totalLinhas() + ".");
        }
        return config;
    }

    /**
     * Faz o parsing de uma lista de formacoes separadas por virgula, removendo
     * duplicatas e preservando a ordem de entrada.
     *
     * @throws IllegalArgumentException quando ha menos de {@value #MIN_FORMACOES}
     *                                  ou mais de {@value #MAX_FORMACOES} formacoes
     *                                  distintas, ou quando alguma formacao e invalida
     */
    public static List<FormacaoConfig> parseLista(String formacoes) {
        if (formacoes == null || formacoes.isBlank()) {
            throw new IllegalArgumentException(
                    "Parametro 'formacoes' e obrigatorio. Informe ao menos "
                            + MIN_FORMACOES + " formacoes separadas por virgula (ex: 4-3-3,3-4-3).");
        }

        Set<FormacaoConfig> distintas = new LinkedHashSet<>();
        for (String parte : formacoes.split(",")) {
            if (parte.isBlank()) {
                continue;
            }
            distintas.add(parse(parte));
        }

        if (distintas.size() < MIN_FORMACOES) {
            throw new IllegalArgumentException(
                    "Informe ao menos " + MIN_FORMACOES + " formacoes distintas para comparar. "
                            + "Formacoes distintas informadas: " + distintas.size() + ".");
        }
        if (distintas.size() > MAX_FORMACOES) {
            throw new IllegalArgumentException(
                    "Maximo de " + MAX_FORMACOES + " formacoes por comparacao. "
                            + "Formacoes distintas informadas: " + distintas.size() + ".");
        }

        return new ArrayList<>(distintas);
    }

    private static int parseNumero(String valor, String formacaoOriginal) {
        try {
            int numero = Integer.parseInt(valor.trim());
            if (numero < 0) {
                throw new NumberFormatException();
            }
            return numero;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Formacao invalida: '" + formacaoOriginal.trim()
                            + "'. Use o formato zag-mei-ata com numeros inteiros (ex: 4-3-3).");
        }
    }
}
