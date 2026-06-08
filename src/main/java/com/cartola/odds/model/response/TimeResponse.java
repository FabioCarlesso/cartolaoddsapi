package com.cartola.odds.model.response;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Time;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
@Schema(description = "Time montado para a rodada do Cartola FC")
public class TimeResponse {

    @Schema(description = "Numero da rodada", example = "15")
    private final int rodada;

    @Schema(
        description = "Aviso sobre o status do mercado. "
                    + "Preenchido quando o mercado nao esta aberto (fechado, manutencao, etc.). "
                    + "Null quando o mercado esta aberto normalmente.",
        example     = "Mercado fechado. Rodada em andamento.",
        nullable    = true
    )
    private final String avisoMercado;

    @Schema(description = "Titulares agrupados por posicao (GOL, LAT, ZAG, MEI, ATA, TEC)")
    private final Map<String, List<AtletaDto>> titulares;

    @Schema(description = "Reservas por posicao (sempre Provaveis, da mesma posicao do titular; TEC nao tem reserva)")
    private final Map<String, AtletaDto> reservas;

    @Schema(description = "Capitao - atleta com maior score (pontuacao dobrada no Cartola)")
    private final AtletaDto capitao;

    @Schema(description = "Reserva de luxo - maior score entre os reservas")
    private final AtletaDto reservaLuxo;

    @Schema(description = "Alertas de titulares em Duvida com substituto sugerido")
    private final List<String> alertasDuvida;

    @Schema(description = "Custo total dos titulares em cartoletas (C$)", example = "142.5")
    private final double custoTotal;

    @Schema(description = "Orcamento maximo informado na requisicao (C$). "
                        + "Null quando nenhum orcamento foi informado.",
            example = "120.0", nullable = true)
    private final Double orcamentoInformado;

    @Schema(description = "Saldo restante de cartoletas (orcamentoInformado - custoTotal). "
                        + "Null quando nenhum orcamento foi informado.",
            example = "1.7", nullable = true)
    private final Double saldoRestante;

    @Schema(description = "Estrategia usada na montagem: SCORE_MAXIMO (sem orcamento) "
                        + "ou CUSTO_BENEFICIO (com orcamento).",
            example = "CUSTO_BENEFICIO",
            allowableValues = {"SCORE_MAXIMO", "CUSTO_BENEFICIO"})
    private final String estrategia;

    @Schema(description = "Indica se todos os slots da formacao foram preenchidos.",
            example = "true")
    private final boolean formacaoCompleta;

    @Schema(description = "Aviso quando o orcamento informado nao foi suficiente para completar "
                        + "a formacao. Null quando completa ou sem orcamento informado.",
            example = "Orcamento de C$50,0 insuficiente para completar a formacao "
                    + "(8/12 titulares escalados). Considere aumentar o orcamento.",
            nullable = true)
    private final String avisoOrcamento;

    public static TimeResponse from(Time time) {
        return TimeResponse.builder()
                .rodada(time.getRodada())
                .avisoMercado(time.getAvisoMercado())
                .titulares(time.getTitulares().entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().name(),
                                e -> e.getValue().stream().map(AtletaDto::from).toList()
                        )))
                .reservas(time.getReservas().entrySet().stream()
                        .collect(Collectors.toMap(
                                e -> e.getKey().name(),
                                e -> AtletaDto.from(e.getValue())
                        )))
                .capitao(time.getCapitao() != null ? AtletaDto.from(time.getCapitao()) : null)
                .reservaLuxo(time.getReservaLuxo() != null ? AtletaDto.from(time.getReservaLuxo()) : null)
                .alertasDuvida(time.getAlertasDuvida())
                .custoTotal(arredondar(time.getCustoTotal()))
                .orcamentoInformado(time.getOrcamentoInformado())
                .saldoRestante(arredondar(time.getSaldoRestante()))
                .estrategia(time.getEstrategia() != null ? time.getEstrategia().name() : null)
                .formacaoCompleta(time.isFormacaoCompleta())
                .avisoOrcamento(time.getAvisoOrcamento())
                .build();
    }

    /** Arredonda cartoletas para 2 casas decimais, evitando artefatos de ponto flutuante na resposta. */
    private static Double arredondar(Double valor) {
        return valor == null ? null : Math.round(valor * 100.0) / 100.0;
    }

    @Getter
    @Builder
    @Schema(description = "Dados de um atleta escalado")
    public static class AtletaDto {

        @Schema(description = "Nome popular do atleta", example = "Hulk")
        private final String apelido;

        @Schema(description = "Sigla do clube", example = "ATM")
        private final String siglaClube;

        @Schema(description = "Nome completo do clube", example = "Atletico Mineiro")
        private final String nomeClube;

        @Schema(description = "Posicao do atleta", example = "ATA",
                allowableValues = {"GOL", "LAT", "ZAG", "MEI", "ATA", "TEC"})
        private final String posicao;

        @Schema(description = "Status de disponibilidade", example = "Provavel")
        private final String status;

        @Schema(description = "Media de pontos na temporada", example = "8.5")
        private final double mediaPontos;

        @Schema(description = "Valorizacao na ultima rodada (C$)", example = "2.3")
        private final double valorizacao;

        @Schema(description = "Preco atual em cartoletas (C$)", example = "18.3")
        private final double preco;

        @Schema(description = "Score calculado pelo sistema", example = "7.8400")
        private final double score;

        @Schema(description = "Desvio padrao das pontuacoes nas ultimas rodadas consideradas. "
                            + "0.0 quando ha menos de 2 rodadas ou sem historico recente.",
                example = "1.2500")
        private final double desvioPadrao;

        @Schema(description = "Quantidade de rodadas com pontuacao usadas no calculo do desempenho. "
                            + "0 quando o atleta nao possui historico recente.",
                example = "5")
        private final int rodadasConsideradas;

        @Schema(description = "Substituto sugerido caso o atleta esteja em Duvida (null se Provavel)")
        private final AtletaDto substitutoProvavel;

        public static AtletaDto from(Atleta a) {
            return AtletaDto.builder()
                    .apelido(a.getApelido())
                    .siglaClube(a.getSiglaClube())
                    .nomeClube(a.getNomeClube())
                    .posicao(a.getPosicao().name())
                    .status(a.getStatus().getLabel())
                    .mediaPontos(a.getMediaPontos())
                    .valorizacao(a.getValorizacao())
                    .preco(a.getPreco())
                    .score(a.getScore())
                    .desvioPadrao(a.getDesvioPadrao())
                    .rodadasConsideradas(a.getRodadasConsideradas())
                    .substitutoProvavel(a.getSubstitutoProvavel() != null
                            ? AtletaDto.from(a.getSubstitutoProvavel()) : null)
                    .build();
        }
    }
}
