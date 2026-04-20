package com.cartola.odds.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Times favoritos da rodada filtrados pela odd limite")
public class FavoritosResponse {

    @Schema(description = "Odd limite aplicada no filtro", example = "3.0")
    private final double oddLimite;

    @Schema(description = "Total de jogos analisados", example = "10")
    private final int totalJogos;

    @Schema(description = "Times que passaram no filtro (favoritos)", example = "4")
    private final int totalFavoritos;

    @Schema(description = "Jogos descartados por nao ter favorito claro (ambos os times acima do limite)", example = "6")
    private final int totalDescartados;

    @Schema(description = "Jogos com times favoritos (odd <= oddLimite)")
    private final List<JogoFavoritoDto> favoritos;

    @Schema(description = "Jogos descartados por nao ter favorito claro")
    private final List<JogoDescartadoDto> descartados;

    @Getter
    @Builder
    @Schema(description = "Jogo com time favorito identificado")
    public static class JogoFavoritoDto {

        @Schema(description = "Time favorito (menor odd do jogo)", example = "Flamengo")
        private final String timeFavorito;

        @Schema(description = "Odd do time favorito", example = "1.95")
        private final double oddFavorito;

        @Schema(description = "Nome do time adversario", example = "Palmeiras")
        private final String timeAdversario;

        @Schema(description = "Odd do time adversario", example = "4.20")
        private final double oddAdversario;

        @Schema(description = "Odd do empate", example = "3.50")
        private final double oddEmpate;

        @Schema(description = "Time favorito joga em casa", example = "true")
        private final boolean favoritoEmCasa;
    }

    @Getter
    @Builder
    @Schema(description = "Jogo descartado por nao ter favorito claro")
    public static class JogoDescartadoDto {

        @Schema(description = "Time da casa", example = "Fortaleza")
        private final String timeCasa;

        @Schema(description = "Odd do time da casa", example = "3.20")
        private final double oddCasa;

        @Schema(description = "Time visitante", example = "Bahia")
        private final String timeVisitante;

        @Schema(description = "Odd do time visitante", example = "3.40")
        private final double oddVisitante;

        @Schema(description = "Odd do empate", example = "3.10")
        private final double oddEmpate;

        @Schema(description = "Razao do descarte", example = "Menor odd (3.20) acima do limite (3.0)")
        private final String motivo;
    }
}
