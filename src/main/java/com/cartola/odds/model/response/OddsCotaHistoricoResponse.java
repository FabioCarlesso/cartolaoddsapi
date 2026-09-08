package com.cartola.odds.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "Serie das leituras de cota da The Odds API dentro de uma janela")
public class OddsCotaHistoricoResponse {

    @Schema(description = "Tamanho da janela consultada, em dias", example = "30")
    private final int dias;

    @Schema(description = "Instante a partir do qual as leituras foram buscadas")
    private final LocalDateTime desde;

    @Schema(description = "Quantidade de leituras na janela", example = "112")
    private final int total;

    @Schema(description = "Leituras em ordem cronologica, da mais antiga para a mais recente")
    private final List<LeituraCotaDto> leituras;

    @Getter
    @Builder
    @Schema(description = "Uma leitura dos headers de cota do provedor")
    public static class LeituraCotaDto {

        @Schema(description = "Instante da leitura")
        private final LocalDateTime instante;

        @Schema(description = "Saldo restante informado pelo provedor. Null quando aquela "
                            + "resposta nao trouxe o header x-requests-remaining.",
                example = "412", nullable = true)
        private final Long saldoRestante;

        @Schema(description = "Consumo do mes informado pelo provedor. Null quando aquela "
                            + "resposta nao trouxe o header x-requests-used.",
                example = "88", nullable = true)
        private final Long consumoMes;

        @Schema(
            description = """
                true na primeira leitura de um ciclo novo de cota: o consumo do mes caiu em \
                relacao a leitura anterior, ou seja, o provedor renovou a cota entre as duas.

                Existe para que quem desenha o grafico nao precise reimplementar a deteccao — \
                e para que a queda do consumo nao seja lida como erro de coleta. Nunca vem true \
                na primeira leitura da janela: sem uma leitura anterior para comparar, a \
                aplicacao nao sabe se houve renovacao, e afirmar que houve seria chute.""",
            example = "false"
        )
        private final boolean reinicioDeCota;
    }
}
