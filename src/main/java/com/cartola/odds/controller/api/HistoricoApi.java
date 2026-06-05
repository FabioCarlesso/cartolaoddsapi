package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.EscalacaoRodadaResponse;
import com.cartola.odds.model.response.HistoricoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrato REST do historico de escalacoes por rodada.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Historico", description = "Historico de escalacoes registradas por rodada e desempenho real das sugestoes")
@RequestMapping("/api/historico")
public interface HistoricoApi {

    @GetMapping
    @Operation(
        summary     = "Listar rodadas registradas",
        description = "Retorna todas as rodadas com escalacao registrada, com resumo de score sugerido vs. pontuacao real."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historico retornado com sucesso",
            content = @Content(schema = @Schema(implementation = HistoricoResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<HistoricoResponse> listar();

    @GetMapping("/{rodadaId}")
    @Operation(
        summary     = "Detalhar escalacao de uma rodada",
        description = "Retorna os atletas, posicoes, scores e flags (capitao, reserva de luxo, duvida) de uma rodada especifica."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Escalacao retornada com sucesso",
            content = @Content(schema = @Schema(implementation = EscalacaoRodadaResponse.class))),
        @ApiResponse(responseCode = "404", description = "Nenhuma escalacao registrada para a rodada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EscalacaoRodadaResponse> detalhar(
            @Parameter(description = "Numero da rodada", example = "14") @PathVariable Integer rodadaId);

    @PostMapping("/{rodadaId}/atualizar-pontuacao")
    @Operation(
        summary     = "Atualizar pontuacao real da rodada",
        description = """
            Consulta o /atletas/pontuados da rodada e preenche a pontuacao real dos atletas registrados.

            **Idempotente:** pode ser chamado novamente para reprocessar a pontuacao.
            Atletas nao encontrados em /pontuados permanecem com pontuacao real nula.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pontuacao real atualizada com sucesso",
            content = @Content(schema = @Schema(implementation = EscalacaoRodadaResponse.class))),
        @ApiResponse(responseCode = "404", description = "Nenhuma escalacao registrada para a rodada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com API externa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<EscalacaoRodadaResponse> atualizarPontuacao(
            @Parameter(description = "Numero da rodada", example = "14") @PathVariable Integer rodadaId);
}
