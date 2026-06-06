package com.cartola.odds.model.response;

import com.cartola.odds.model.EscalacaoRodada;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Escalacao detalhada de uma rodada")
public class EscalacaoRodadaResponse {

    @Schema(description = "Numero da rodada", example = "14")
    private final int rodadaId;

    @Schema(description = "Atletas registrados na rodada")
    private final List<AtletaEscalado> atletas;

    public static EscalacaoRodadaResponse from(int rodadaId, List<EscalacaoRodada> escalacoes) {
        return EscalacaoRodadaResponse.builder()
                .rodadaId(rodadaId)
                .atletas(escalacoes.stream().map(AtletaEscalado::from).toList())
                .build();
    }

    @Getter
    @Builder
    @Schema(description = "Atleta registrado em uma rodada")
    public static class AtletaEscalado {

        @Schema(description = "Nome popular do atleta", example = "Hulk")
        private final String apelido;

        @Schema(description = "Posicao do atleta", example = "ATA")
        private final String posicao;

        @Schema(description = "Nome do clube", example = "Atletico MG")
        private final String clube;

        @Schema(description = "Score sugerido pelo sistema", example = "9.2")
        private final double scoreSugerido;

        @Schema(description = "Pontuacao real obtida na rodada. Null enquanto a rodada nao for atualizada.",
                example = "8.5", nullable = true)
        private final Double pontuacaoReal;

        @Schema(description = "Indica se o atleta foi escalado como capitao", example = "true")
        private final boolean capitao;

        @Schema(description = "Indica se o atleta e a reserva de luxo", example = "false")
        private final boolean reservaLuxo;

        @Schema(description = "Indica se o atleta estava em duvida no momento da escalacao", example = "false")
        private final boolean emDuvida;

        public static AtletaEscalado from(EscalacaoRodada e) {
            return AtletaEscalado.builder()
                    .apelido(e.getApelido())
                    .posicao(e.getPosicao())
                    .clube(e.getClube())
                    .scoreSugerido(e.getScoreSugerido())
                    .pontuacaoReal(e.getPontuacaoReal())
                    .capitao(e.isCapitao())
                    .reservaLuxo(e.isReservaLuxo())
                    .emDuvida(e.isEmDuvida())
                    .build();
        }
    }
}
