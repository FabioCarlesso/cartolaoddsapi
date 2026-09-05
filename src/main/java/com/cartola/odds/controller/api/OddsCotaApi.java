package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.OddsCotaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrato REST do endpoint de cota da The Odds API.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Odds Cota", description = "Consumo e guardrail de cota da The Odds API (restrito a ADMIN)")
@RequestMapping("/api/odds/cota")
public interface OddsCotaApi {

    @GetMapping
    @Operation(
        summary     = "Consultar cota de consumo da The Odds API",
        description = """
            Devolve o saldo restante e o consumo do mes informados pela The Odds API no ultimo
            header lido (x-requests-remaining / x-requests-used), o instante dessa leitura e se
            o guardrail de cota (odds.api.min-requests-remaining) esta ativo.

            Com o guardrail ativo, o OddsClient para de chamar o provedor e passa a servir a
            ultima resposta conhecida, persistida em odds_snapshot.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado da cota retornado com sucesso",
            content = @Content(schema = @Schema(implementation = OddsCotaResponse.class))),
        @ApiResponse(responseCode = "401", description = "Sem token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Token sem perfil ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OddsCotaResponse> buscarCota();
}
