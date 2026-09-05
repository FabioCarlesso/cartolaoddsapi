package com.cartola.odds.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Estado do consumo de cota da The Odds API e do guardrail configurado")
public class OddsCotaResponse {

    @Schema(description = "Saldo de requisicoes restantes no ultimo header lido do provedor. "
                        + "Null quando nenhuma leitura ocorreu desde o boot.",
            example = "412", nullable = true)
    private final Long saldoRestante;

    @Schema(description = "Requisicoes consumidas no mes, no ultimo header lido do provedor. "
                        + "Null quando nenhuma leitura ocorreu desde o boot.",
            example = "88", nullable = true)
    private final Long consumoMes;

    @Schema(description = "Instante da ultima leitura de cota nos headers do provedor. "
                        + "Null quando nenhuma leitura ocorreu desde o boot.",
            nullable = true)
    private final LocalDateTime ultimaLeitura;

    @Schema(description = "Minimo de requisicoes restantes configurado (odds.api.min-requests-remaining). "
                        + "Abaixo dele o guardrail entra em acao.",
            example = "50")
    private final int minRequestsRemaining;

    @Schema(description = "true quando o guardrail esta ativo: o saldo conhecido esta abaixo do minimo "
                        + "e o cliente parou de chamar o provedor, servindo a ultima resposta conhecida.",
            example = "false")
    private final boolean guardrailAtivo;
}
