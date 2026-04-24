package com.cartola.odds.controller.api;

import com.cartola.odds.model.request.ConfiguracaoRequest;
import com.cartola.odds.model.response.ConfiguracaoResponse;
import com.cartola.odds.model.response.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrato REST do endpoint de configuracao de parametros.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Configuracao", description = "Gerenciamento dos parametros de negocio em runtime (odd limite, pesos do score, formacao e regras)")
@RequestMapping("/api/config")
public interface ConfiguracaoApi {

    @GetMapping
    @Operation(
        summary     = "Consultar configuracao ativa",
        description = "Retorna os parametros de negocio ativos no momento: odd limite, pesos do score ponderado, formacao e regras."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuracao retornada com sucesso",
            content = @Content(schema = @Schema(implementation = ConfiguracaoResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ConfiguracaoResponse> buscar();

    @PatchMapping
    @Operation(
        summary     = "Atualizar parametros em runtime",
        description = """
            Atualiza um ou mais parametros de negocio sem reiniciar a aplicacao.
            Todos os campos sao opcionais — envie apenas o que deseja alterar.

            **Regras de validacao:**
            - `oddLimite` deve ser > 1.0
            - Cada peso deve estar entre 0.0 e 1.0
            - A **soma dos 5 pesos** deve ser exatamente 1.0 (tolerancia ±0.01)
            - `evitarMesmoClubeDefesa` ativa/desativa a regra de nao repetir clubes entre GOL, LAT e ZAG

            **Efeito imediato:** o cache de configuracao e invalidado apos a atualizacao.
            A proxima chamada a qualquer endpoint ja usa os novos valores.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuracao atualizada com sucesso",
            content = @Content(schema = @Schema(implementation = ConfiguracaoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Parametro invalido ou soma dos pesos != 1.0",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ConfiguracaoResponse> atualizar(@Valid @RequestBody ConfiguracaoRequest request);

    @PostMapping("/reset")
    @Operation(
        summary     = "Resetar para os valores padrao",
        description = """
            Restaura todos os parametros para os valores padrao de fabrica:
            - `oddLimite`: 3.0
            - Pesos: mediaPontos=0.40, valorizacao=0.20, desempenho=0.20, fatorCasa=0.10, timeFavorito=0.10
            - Formacao: GOL=1, LAT=2, ZAG=2, MEI=3, ATA=3, TEC=1
            - Regra de defesa: evitarMesmoClubeDefesa=true
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuracao resetada com sucesso",
            content = @Content(schema = @Schema(implementation = ConfiguracaoResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ConfiguracaoResponse> resetar();
}
