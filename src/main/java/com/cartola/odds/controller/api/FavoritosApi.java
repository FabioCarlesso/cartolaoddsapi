package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.FavoritosResponse;
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
 * Contrato REST do endpoint de times favoritos.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Favoritos", description = "Times favoritos da rodada com base nas odds do Brasileirao")
@RequestMapping("/api/favoritos")
public interface FavoritosApi {

    @GetMapping
    @Operation(
        summary     = "Listar times favoritos da rodada",
        description = """
            Busca as odds do Brasileirao e classifica cada jogo em:

            **Favoritos** — time com menor odd ≤ `oddLimite`.
            Retorna nome, adversario, odds e se joga em casa.

            **Descartados** — menor odd > `oddLimite` (jogo equilibrado).
            Retorna ambos os times, odds e motivo legivel do descarte.

            **Parametro `oddLimite`** *(opcional)*:
            - Padrao: valor de `cartola.odd-limite` no `application.properties` (3.0)
            - Simule cenarios: `oddLimite=2.5` retorna apenas favoritos muito claros
            - Validacao: deve ser > 1.0

            **Cache:** odds cacheadas por 10 minutos (Caffeine).
            Sem API Key: retorna `totalJogos=0`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Favoritos listados com sucesso",
            content = @Content(schema = @Schema(implementation = FavoritosResponse.class))),
        @ApiResponse(responseCode = "400", description = "oddLimite invalido (deve ser > 1.0)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com a Odds API",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<FavoritosResponse> listarFavoritos(

        @Parameter(description = "Odd maxima para considerar time favorito. Deve ser > 1.0.",
                   example = "3.0")
        @RequestParam(required = false) Double oddLimite
    );
}
