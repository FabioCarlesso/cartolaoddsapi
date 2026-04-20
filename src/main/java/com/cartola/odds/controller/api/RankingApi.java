package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.RankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Contrato REST do endpoint de ranking de atletas.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Ranking", description = "Ranking dos melhores atletas disponiveis por score")
@RequestMapping("/api/ranking")
public interface RankingApi {

    @GetMapping
    @Operation(
        summary     = "Top atletas por score",
        description = """
            Retorna os melhores atletas ordenados por score decrescente.
            Aplica os mesmos filtros do `/api/time` (favoritos, status, preco).

            **Score calculado com desempenho real:**
            - Media das ultimas 5 rodadas via /atletas/pontuados
            - Fallback para media da temporada quando historico indisponivel

            **Parametros:**
            - `posicao` *(opcional)*: GOL, LAT, ZAG, MEI, ATA ou TEC. Omitir = todas.
            - `limite` *(opcional)*: 1-100 (padrao 25). Valores fora do range sao clampados.

            **Exemplos:**
            - `GET /api/ranking` → top 25 geral
            - `GET /api/ranking?posicao=ATA` → top 25 atacantes
            - `GET /api/ranking?posicao=MEI&limite=10` → top 10 meias
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ranking retornado com sucesso",
            content = @Content(schema = @Schema(implementation = RankingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Posicao invalida",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum atleta disponivel",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com API externa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RankingResponse> ranking(

        @Parameter(description = "Posicao para filtrar. Omitir retorna todas.",
                   schema = @Schema(allowableValues = {"GOL", "LAT", "ZAG", "MEI", "ATA", "TEC"}))
        @RequestParam(required = false) String posicao,

        @Parameter(description = "Numero de atletas a retornar (1-100)", example = "25")
        @RequestParam(defaultValue = "25") int limite
    );
}
