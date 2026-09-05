package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.CacheResponse;
import com.cartola.odds.model.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrato REST do endpoint de gerenciamento de cache.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Cache", description = "Gerenciamento e invalidacao do cache em memoria (Caffeine)")
@RequestMapping("/api/cache")
public interface CacheApi {

    @DeleteMapping
    @Operation(
        summary     = "Invalidar todos os caches",
        description = """
            Limpa imediatamente todas as entradas de todos os caches em memoria (Caffeine).

            **Caches afetados:**
            - `odds` — respostas da The Odds API (TTL configuravel, padrao 60 min; resposta sem jogos, 10 min)
            - `atletas` — /atletas/mercado (TTL 15 min)
            - `clubes` — /clubes (TTL 60 min)
            - `partidas` — /partidas (TTL 15 min)
            - `pontuados` — /atletas/pontuados (TTL 15 min)
            - `statusMercado` — /mercado/status (TTL 2 min)

            Apos a invalidacao, a proxima requisicao busca dados frescos nas APIs externas.

            **Cota da The Odds API:** invalidar o cache `odds` gasta uma requisicao da cota paga
            na proxima busca. O guardrail continua valendo — com o saldo abaixo de
            `odds.api.min-requests-remaining`, a busca seguinte e servida pelo snapshot
            persistido em vez de chamar o provedor.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Todos os caches invalidados com sucesso",
            content = @Content(schema = @Schema(implementation = CacheResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno ao invalidar cache",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CacheResponse> invalidarTodos();

    @DeleteMapping("/{nome}")
    @Operation(
        summary     = "Invalidar cache especifico",
        description = """
            Limpa imediatamente todas as entradas de um cache especifico pelo nome.

            **Nomes de cache validos:**
            - `odds`
            - `atletas`
            - `clubes`
            - `partidas`
            - `pontuados`
            - `statusMercado`
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cache invalidado com sucesso",
            content = @Content(schema = @Schema(implementation = CacheResponse.class))),
        @ApiResponse(responseCode = "400", description = "Nome de cache invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno ao invalidar cache",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CacheResponse> invalidarPorNome(
        @Parameter(
            description = "Nome do cache a invalidar",
            schema = @Schema(allowableValues = {"odds", "atletas", "clubes", "partidas", "pontuados", "statusMercado"})
        )
        @PathVariable String nome
    );
}
