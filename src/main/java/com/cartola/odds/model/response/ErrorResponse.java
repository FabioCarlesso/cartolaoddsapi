package com.cartola.odds.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Resposta de erro padrao da API")
public class ErrorResponse {

    @Schema(description = "Codigo HTTP do erro", example = "422")
    private final int status;

    @Schema(description = "Tipo do erro", example = "Erro no pipeline")
    private final String erro;

    @Schema(description = "Mensagem detalhada", example = "Nenhum atleta disponivel apos filtragem.")
    private final String mensagem;

    @Schema(description = "Data e hora do erro")
    private final LocalDateTime timestamp;

    public static ErrorResponse of(int status, String erro, String mensagem) {
        return ErrorResponse.builder()
                .status(status)
                .erro(erro)
                .mensagem(mensagem)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
