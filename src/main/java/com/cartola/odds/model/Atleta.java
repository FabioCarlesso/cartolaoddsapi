package com.cartola.odds.model;

import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

@Getter
@Builder
@With
public class Atleta {

    private final int    atletaId;
    private final String apelido;
    private final Posicao posicao;
    private final int    clubeId;
    private final String nomeClube;
    private final String siglaClube;
    private final String nomeClubeNorm;
    private final StatusAtleta status;
    private final double mediaPontos;
    private final double valorizacao;
    private final double preco;

    // Scouts acumulados da temporada (0 quando não disponível na resposta da API)
    private final int defesasDificeis;   // DD - defesas difíceis (goleiros)
    private final int golsSofridos;      // GS - gols sofridos (goleiros)
    private final int penaltisDefendidos; // DP - pênaltis defendidos (goleiros)
    private final int gols;              // G  - gols marcados (atacantes)
    private final int assistencias;      // A  - assistências (atacantes)

    /**
     * Media de pontuacao das ultimas {@code DesempenhoService.RODADAS_HISTORICO} rodadas.
     * Preenchido pelo ScoreService quando o historico estiver disponivel.
     * Quando 0.0, o ScoreService usa mediaPontos como fallback.
     */
    private final double desempenhoRecente;

    private final double score;
    private final Atleta substitutoProvavel;

    /** Formata "Apelido (SIG)" com alerta quando em duvida. */
    public String formatado() {
        var base = "%s (%s)".formatted(apelido, siglaClube);
        return status == StatusAtleta.DUVIDA ? base + "  ⚠️ DÚVIDA" : base;
    }

    public boolean isDuvida() {
        return status == StatusAtleta.DUVIDA;
    }

    public boolean isProvavel() {
        return status == StatusAtleta.PROVAVEL;
    }
}
