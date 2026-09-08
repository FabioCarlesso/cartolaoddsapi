package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.OddsCotaHistoricoResponse;
import com.cartola.odds.model.response.OddsCotaResponse;
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
            ultima resposta conhecida, persistida em odds_snapshot. Nesse estado,
            proximaSondagem diz quando uma chamada volta a ser liberada para reavaliar o saldo
            (odds.api.sonda-intervalo-horas) — ou seja, quando o guardrail pode se destravar
            sozinho, sem intervencao.
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

    @GetMapping("/historico")
    @Operation(
        summary     = "Consultar o historico de leituras de cota",
        description = """
            Devolve a serie das leituras de cota dentro de uma janela, em ordem cronologica —
            o que permite ver o consumo ao longo do mes, e nao apenas o estado atual que
            GET /api/odds/cota entrega.

            Uma leitura e gravada a cada resposta do provedor que traz os headers de cota,
            inclusive as respostas de erro. Uma sondagem liberada pelo guardrail que nao le
            header nenhum nao gera leitura: ela nao mediu nada.

            Cada item traz reinicioDeCota=true quando, em relacao a leitura anterior, o consumo
            caiu ou o saldo subiu — os dois sinais da renovacao da cota pelo provedor. Sem essa
            marca, a queda do consumo na virada do ciclo pareceria falha de coleta.

            FUSO HORARIO: instante e desde sao LocalDateTime, data e hora locais do servidor,
            sem offset. Um cliente que faca new Date(instante) vai interpreta-los como hora
            local dele — com servidor em UTC e navegador em UTC-3, todo ponto do grafico
            desloca 3 h. Converta usando o fuso em que a aplicacao roda, nao o do navegador.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Serie retornada com sucesso",
            content = @Content(schema = @Schema(implementation = OddsCotaHistoricoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Janela invalida (dias fora de 1..92)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Sem token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Token sem perfil ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OddsCotaHistoricoResponse> buscarHistorico(
        @Parameter(description = "Tamanho da janela em dias, contada a partir de agora (1 a 92). "
                            + "A serie nao e agregada: 30 dias sao ~500 itens, 92 dias ~1.500.",
                   example = "30")
        @RequestParam(defaultValue = "30") int dias
    );
}
