package com.cartola.odds.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "Historico de escalacoes registradas por rodada")
public class HistoricoResponse {

    @Schema(description = "Total de rodadas com escalacao registrada", example = "3")
    private final int totalRodadas;

    @Schema(description = "Resumo de cada rodada registrada, ordenado por rodada")
    private final List<RodadaResumo> rodadas;

    @Getter
    @Builder
    @Schema(description = "Resumo de score sugerido vs. pontuacao real de uma rodada")
    public static class RodadaResumo {

        @Schema(description = "Numero da rodada", example = "14")
        private final int rodadaId;

        @Schema(description = "Momento em que a escalacao foi registrada", example = "2025-05-10T10:30:00")
        private final LocalDateTime criadoEm;

        @Schema(description = "Quantidade de atletas registrados na rodada", example = "12")
        private final int totalAtletas;

        @Schema(description = "Soma dos scores sugeridos dos atletas da rodada", example = "94.3")
        private final double scoreSugeridoTotal;

        @Schema(description = "Soma das pontuacoes reais (capitao dobrado). Null enquanto a rodada nao for atualizada.",
                example = "87.5", nullable = true)
        private final Double pontuacaoRealTotal;

        @Schema(description = "Indica se a pontuacao real ja foi calculada para a rodada", example = "true")
        private final boolean pontuacaoRealDisponivel;
    }
}
