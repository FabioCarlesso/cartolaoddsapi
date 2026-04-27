package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.ExportacaoResponse;
import com.cartola.odds.model.response.RelatorioPagamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Contrato REST do relatorio de pagamento dos profissionais (atletas escalados).
 * Inclui rotas Angular-friendly (JSON com base64) e download direto (binario).
 */
@Tag(name = "Relatorio de Pagamento",
     description = "Relatorio de pagamento dos profissionais escalados na rodada — JSON, PDF ou Excel")
@RequestMapping("/api/relatorio/pagamento")
public interface RelatorioPagamentoApi {

    @GetMapping
    @Operation(
        summary     = "Relatorio de pagamento (JSON)",
        description = """
            Retorna o relatorio de pagamento dos profissionais escalados na rodada
            em formato JSON, ja preparado para consumo direto no Angular
            (camelCase, datas ISO-8601, nullable explicito).

            **Inclui:**
            - rodada e aviso de mercado
            - data e hora da geracao
            - total de profissionais e total de pagamento
            - lista de itens (titulares + reservas) ordenada por valor decrescente
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Relatorio gerado com sucesso",
            content = @Content(schema = @Schema(implementation = RelatorioPagamentoResponse.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum atleta disponivel apos filtragem",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com API externa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<RelatorioPagamentoResponse> obter();

    @GetMapping("/exportar")
    @Operation(
        summary     = "Exportar relatorio em PDF ou Excel (Angular-friendly)",
        description = """
            Gera o relatorio no formato indicado e devolve um JSON contendo o
            arquivo codificado em **Base64**, junto com metadados (nome, mime type,
            tamanho, data de geracao). Esse contrato e amigavel ao Angular: o
            frontend decodifica o base64, cria um `Blob` e dispara o download
            sem precisar lidar com headers binarios.

            **Formatos aceitos:** `PDF` (default) ou `EXCEL`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arquivo gerado com sucesso",
            content = @Content(schema = @Schema(implementation = ExportacaoResponse.class))),
        @ApiResponse(responseCode = "400", description = "Formato invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum atleta disponivel apos filtragem",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com API externa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ExportacaoResponse> exportar(
            @Parameter(description = "Formato do arquivo gerado",
                       schema = @Schema(allowableValues = {"PDF", "EXCEL"}, defaultValue = "PDF"))
            @RequestParam(defaultValue = "PDF") String formato
    );

    @GetMapping("/download")
    @Operation(
        summary     = "Download direto do relatorio em PDF ou Excel",
        description = """
            Mesma geracao da rota `/exportar`, porem entrega o conteudo binario
            puro com `Content-Type` e `Content-Disposition: attachment`.
            Util quando o frontend prefere abrir uma aba para download direto
            ao inves de manipular base64.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Arquivo entregue",
            content = @Content(mediaType = "application/octet-stream")),
        @ApiResponse(responseCode = "400", description = "Formato invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum atleta disponivel apos filtragem",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Resource> download(
            @Parameter(description = "Formato do arquivo gerado",
                       schema = @Schema(allowableValues = {"PDF", "EXCEL"}, defaultValue = "PDF"))
            @RequestParam(defaultValue = "PDF") String formato
    );
}
