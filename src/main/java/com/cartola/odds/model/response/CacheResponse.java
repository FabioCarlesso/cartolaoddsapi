package com.cartola.odds.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "Resposta da operacao de invalidacao de cache")
public class CacheResponse {

    @Schema(description = "Caches que foram invalidados", example = "[\"atletas\", \"odds\"]")
    private final List<String> cachesInvalidados;

    @Schema(description = "Mensagem descritiva da operacao", example = "Cache invalidado com sucesso.")
    private final String mensagem;

    @Schema(description = "Data e hora da operacao")
    private final LocalDateTime timestamp;

    public static CacheResponse of(List<String> caches, String mensagem) {
        return CacheResponse.builder()
                .cachesInvalidados(caches)
                .mensagem(mensagem)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
