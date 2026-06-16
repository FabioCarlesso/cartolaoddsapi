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
        List<ResultadoFormacao> ordenados = resultados.stream()
                .sorted(Comparator.comparingDouble(
                        (ResultadoFormacao r) -> scoreTotalTitulares(r.time())).reversed())
                .toList();

        List<ResultadoDto> dtos = IntStream.range(0, ordenados.size())
                .mapToObj(i -> ResultadoDto.from(ordenados.get(i), i + 1))
                .toList();

        int rodada = ordenados.isEmpty() ? 0 : ordenados.get(0).time().getRodada();
        String melhor = dtos.isEmpty() ? null : dtos.get(0).getFormacao();

        return CompararFormacoesResponse.builder()
                .rodada(rodada)
                .formacoesComparadas(dtos.size())
                .melhorFormacao(melhor)
                .resultados(dtos)
                .build();
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

        @Schema(description = "Time completo montado para esta formacao")
        private final TimeResponse time;

        static ResultadoDto from(ResultadoFormacao resultado, int posicao) {
            Time time = resultado.time();
            return ResultadoDto.builder()
                    .formacao(resultado.formacao().label())
                    .scoreTotal(arredondar(scoreTotalTitulares(time)))
                    .custoTotal(arredondar(time.getCustoTotal()))
                    .capitao(time.getCapitao() != null ? time.getCapitao().formatado() : null)
                    .posicao(posicao)
                    .time(TimeResponse.from(time))
                    .build();
        }
    }
}
