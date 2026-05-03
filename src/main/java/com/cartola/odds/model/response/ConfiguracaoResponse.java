package com.cartola.odds.model.response;

import com.cartola.odds.model.Configuracao;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Configuracao ativa da aplicacao")
public class ConfiguracaoResponse {

    @Schema(description = "Odd maxima para considerar um time favorito", example = "3.0")
    private final double oddLimite;

    @Schema(description = "Peso da media de pontos da temporada", example = "0.40")
    private final double pesoMediaPontos;

    @Schema(description = "Peso da valorizacao", example = "0.20")
    private final double pesoValorizacao;

    @Schema(description = "Peso do desempenho recente", example = "0.20")
    private final double pesoDesempenho;

    @Schema(description = "Peso do bonus de mandante", example = "0.10")
    private final double pesoFatorCasa;

    @Schema(description = "Peso do bonus de time favorito", example = "0.10")
    private final double pesoTimeFavorito;

    @Schema(description = "Numero de goleiros na formacao", example = "1")
    private final int formacaoGol;

    @Schema(description = "Numero de laterais na formacao", example = "2")
    private final int formacaoLat;

    @Schema(description = "Numero de zagueiros na formacao", example = "2")
    private final int formacaoZag;

    @Schema(description = "Numero de meias na formacao", example = "3")
    private final int formacaoMei;

    @Schema(description = "Numero de atacantes na formacao", example = "3")
    private final int formacaoAta;

    @Schema(description = "Numero de tecnicos na formacao", example = "1")
    private final int formacaoTec;

    @Schema(description = "Impede repetir clubes entre titulares GOL, LAT e ZAG", example = "true")
    private final boolean evitarMesmoClubeDefesa;

    @Schema(description = "Limite maximo de titulares do mesmo clube (inclui TEC)", example = "4")
    private final int limiteAtletasPorClube;

    @Schema(description = "Budget maximo em C$ para os titulares (0 = sem limite)", example = "100.0")
    private final double budgetMaximo;

    @Schema(description = "Data e hora da ultima atualizacao")
    private final LocalDateTime updatedAt;

    public static ConfiguracaoResponse from(Configuracao c) {
        return ConfiguracaoResponse.builder()
                .oddLimite(c.getOddLimite())
                .pesoMediaPontos(c.getPesoMediaPontos())
                .pesoValorizacao(c.getPesoValorizacao())
                .pesoDesempenho(c.getPesoDesempenho())
                .pesoFatorCasa(c.getPesoFatorCasa())
                .pesoTimeFavorito(c.getPesoTimeFavorito())
                .formacaoGol(c.getFormacaoGol())
                .formacaoLat(c.getFormacaoLat())
                .formacaoZag(c.getFormacaoZag())
                .formacaoMei(c.getFormacaoMei())
                .formacaoAta(c.getFormacaoAta())
                .formacaoTec(c.getFormacaoTec())
                .evitarMesmoClubeDefesa(c.isEvitarMesmoClubeDefesa())
                .limiteAtletasPorClube(c.getLimiteAtletasPorClube())
                .budgetMaximo(c.getBudgetMaximo())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
