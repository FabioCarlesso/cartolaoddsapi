package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.TimeResponse;
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
 * Contrato REST do endpoint de montagem de time.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Time", description = "Montagem automatica do time do Cartola FC baseada em odds")
@RequestMapping("/api/time")
public interface TimeApi {

    @GetMapping
    @Operation(
        summary     = "Montar time da rodada",
        description = """
            Executa o pipeline completo e retorna o time montado para a rodada atual.

            **Etapas do pipeline:**
            1. Verifica status do mercado Cartola
            2. Busca odds e identifica times favoritos (odd ≤ ODD_LIMITE)
            3. Filtra atletas por status (Provavel/Duvida), preco e time favorito
            4. Busca media real das ultimas 5 rodadas via /atletas/pontuados
            5. Calcula score ponderado com desempenho real
            6. Monta time na formacao 4-3-3

            **Regras aplicadas:**
            - Somente atletas de times favoritos (odd ≤ ODD_LIMITE, padrao 3.0)
            - Somente Provavel (7) e Duvida (6)
            - Formacao: 1 GOL · 2 LAT · 2 ZAG · 3 MEI · 3 ATA · 1 TEC
            - Reservas: somente Provaveis, mesma posicao, preferencialmente mais baratos
            - Titulares em Duvida recebem substituto provavel da mesma posicao
            - Capitao: maior score — prioridade ATA > MEI > ZAG > LAT > GOL > TEC

            **Cache:** respostas das APIs externas cacheadas por 10-60 min (Caffeine).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Time montado com sucesso",
            content = @Content(schema = @Schema(implementation = TimeResponse.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum atleta disponivel apos filtragem",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com API externa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno inesperado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<TimeResponse> montarTime();
}
