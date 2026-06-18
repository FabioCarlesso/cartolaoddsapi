package com.cartola.odds.controller.api;

import com.cartola.odds.model.response.CompararFormacoesResponse;
import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.TimeResponse;
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
            - Reservas: somente Provaveis, mesma posicao, preferencialmente mais baratos; TEC nao tem reserva
            - Titulares em Duvida recebem substituto provavel da mesma posicao
            - Capitao: maior score — prioridade ATA > MEI > ZAG > LAT > GOL > TEC

            **Parametro `orcamento`** *(opcional)*:
            - Nao informado: comportamento padrao (estrategia SCORE_MAXIMO, sem restricao de custo)
            - Informado: o time respeita o limite de cartoletas e os candidatos passam a ser
              ordenados por custo-beneficio (score/preco) — estrategia CUSTO_BENEFICIO
            - Validacao: deve ser > 0

            **Cache:** respostas das APIs externas cacheadas por 10-60 min (Caffeine).
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Time montado com sucesso",
            content = @Content(schema = @Schema(implementation = TimeResponse.class))),
        @ApiResponse(responseCode = "400", description = "orcamento invalido (deve ser > 0)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum atleta disponivel apos filtragem",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com API externa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno inesperado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<TimeResponse> montarTime(

        @Parameter(description = "Orcamento maximo em cartoletas (C$). Quando informado, o time "
                              + "respeita esse limite priorizando custo-beneficio. Deve ser > 0.",
                   example = "120.0")
        @RequestParam(required = false) Double orcamento
    );

    @GetMapping("/comparar")
    @Operation(
        summary     = "Comparar o melhor time entre multiplas formacoes",
        description = """
            Monta o melhor time para cada formacao informada usando o mesmo pool de
            atletas da rodada atual e retorna um comparativo ordenado por scoreTotal.

            Util para descobrir qual formacao extrai mais pontos dos atletas
            disponiveis antes de escalar. **Nao altera** a configuracao persistida.

            **Parametro `formacoes`** *(obrigatorio)*:
            - Lista separada por virgula, no formato `def-mei-ata` (ex: `4-3-3,3-4-3,4-4-2`),
              onde o primeiro numero e o total de defensores (laterais + zagueiros), como na
              notacao do Cartola FC. A defesa e derivada com LAT fixo em 2 e ZAG = def - 2.
            - A soma das posicoes de linha de cada formacao (def + mei + ata) deve ser 10
            - Minimo de 2 e maximo de 5 formacoes distintas
            - Formacoes duplicadas sao ignoradas silenciosamente
            - Formacoes validas: `4-3-3`, `3-4-3`, `4-4-2`, `5-3-2`, `4-5-1`
            - Formacoes invalidas (soma != 10): `4-3-2`, `4-4-1`

            **Parametro `orcamento`** *(opcional)*:
            - Quando informado, cada formacao e montada respeitando o limite de
              cartoletas (estrategia custo-beneficio). Deve ser > 0.

            **Resposta:**
            - `resultados` ordenados por `scoreTotal` decrescente, com campo `posicao` (ranking)
            - `melhorFormacao` aponta para a formacao de maior `scoreTotal`
            - `scoreTotal` soma apenas os titulares, para comparacao justa
            - As mesmas regras de montagem se aplicam a cada formacao (limite por
              clube, defesa sem clube repetido, dúvidas com reserva)
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comparativo montado com sucesso",
            content = @Content(schema = @Schema(implementation = CompararFormacoesResponse.class))),
        @ApiResponse(responseCode = "400",
            description = "Parametro invalido (menos de 2 formacoes, mais de 5, formacao com soma != 10 ou orcamento <= 0)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Nenhum atleta disponivel apos filtragem",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Erro de comunicacao com API externa",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Erro interno inesperado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<CompararFormacoesResponse> compararFormacoes(

        @Parameter(description = "Formacoes a comparar (def-mei-ata, def = laterais + zagueiros), "
                              + "separadas por virgula. Soma de cada formacao deve ser 10. "
                              + "Minimo 2, maximo 5 distintas.",
                   example = "4-3-3,3-4-3,4-4-2", required = true)
        @RequestParam(required = false) String formacoes,

        @Parameter(description = "Orcamento maximo em cartoletas (C$) aplicado a cada formacao. "
                              + "Quando informado, deve ser > 0.",
                   example = "120.0")
        @RequestParam(required = false) Double orcamento
    );
}
