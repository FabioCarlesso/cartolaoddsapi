package com.cartola.odds.model.response;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.ResultadoFormacao;
import com.cartola.odds.model.Time;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Getter
@Builder
@Schema(description = "Comparativo do melhor time montado para cada formacao informada")
public class CompararFormacoesResponse {

    @Schema(description = "Numero da rodada", example = "15")
    private final int rodada;

    @Schema(description = "Quantidade de formacoes comparadas", example = "3")
    private final int formacoesComparadas;

    @Schema(description = "Formacao com maior scoreTotal entre as comparadas", example = "4-3-3")
    private final String melhorFormacao;

    @Schema(description = "Resultados ordenados por scoreTotal decrescente")
    private final List<ResultadoDto> resultados;

    public static CompararFormacoesResponse from(List<ResultadoFormacao> resultados) {
        // Calcula o scoreTotal uma unica vez por resultado antes de ordenar,
        // evitando recomputacao a cada comparacao do sort.
        List<ResultadoComScore> comScore = resultados.stream()
                .map(r -> new ResultadoComScore(r, scoreTotalTitulares(r.time())))
                .sorted(Comparator.comparingDouble(ResultadoComScore::scoreTotal).reversed())
                .toList();

        List<ResultadoDto> dtos = IntStream.range(0, comScore.size())
                .mapToObj(i -> ResultadoDto.from(comScore.get(i), i + 1))
                .toList();

        int rodada = comScore.isEmpty() ? 0 : comScore.get(0).resultado().time().getRodada();
        String melhor = dtos.isEmpty() ? null : dtos.get(0).getFormacao();

        return CompararFormacoesResponse.builder()
                .rodada(rodada)
                .formacoesComparadas(dtos.size())
                .melhorFormacao(melhor)
                .resultados(dtos)
                .build();
    }

    /** Resultado emparelhado com seu scoreTotal ja calculado, para ordenacao sem recomputacao. */
    private record ResultadoComScore(ResultadoFormacao resultado, double scoreTotal) {
    }

    /** Soma o score apenas dos titulares, para comparacao justa entre formacoes. */
    private static double scoreTotalTitulares(Time time) {
        return time.getTitulares().values().stream()
                .flatMap(List::stream)
                .mapToDouble(Atleta::getScore)
                .sum();
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    @Getter
    @Builder
    @Schema(description = "Time montado para uma formacao especifica, com score e ranking")
    public static class ResultadoDto {

        @Schema(description = "Formacao avaliada (zag-mei-ata)", example = "4-3-3")
        private final String formacao;

        @Schema(description = "Soma do score dos titulares", example = "94.3")
        private final double scoreTotal;

        @Schema(description = "Custo total dos titulares em cartoletas (C$)", example = "138.5")
        private final double custoTotal;

        @Schema(description = "Capitao do time montado nesta formacao", example = "Hulk (ATM)",
                nullable = true)
        private final String capitao;

        @Schema(description = "Posicao no ranking entre as formacoes comparadas (1 = melhor)",
                example = "1")
        private final int posicao;

        @Schema(description = "Indica se todos os slots desta formacao foram preenchidos. "
                            + "false quando o pool (ou o orcamento) nao permitiu completar a formacao.",
                example = "true")
        private final boolean formacaoCompleta;

        @Schema(description = "Aviso quando o orcamento informado nao foi suficiente para completar "
                            + "esta formacao. Null quando completa ou sem orcamento informado.",
                nullable = true)
        private final String avisoOrcamento;

        @Schema(description = "Time completo montado para esta formacao")
        private final TimeResponse time;

        static ResultadoDto from(ResultadoComScore comScore, int posicao) {
            Time time = comScore.resultado().time();
            return ResultadoDto.builder()
                    .formacao(comScore.resultado().formacao().label())
                    .scoreTotal(arredondar(comScore.scoreTotal()))
                    .custoTotal(arredondar(time.getCustoTotal()))
                    .capitao(time.getCapitao() != null ? time.getCapitao().formatado() : null)
                    .posicao(posicao)
                    .formacaoCompleta(time.isFormacaoCompleta())
                    .avisoOrcamento(time.getAvisoOrcamento())
                    .time(TimeResponse.from(time))
                    .build();
        }
    }
}
