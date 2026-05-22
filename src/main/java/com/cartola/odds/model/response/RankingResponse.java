package com.cartola.odds.model.response;

import com.cartola.odds.model.Atleta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "Ranking dos melhores atletas disponiveis na rodada")
public class RankingResponse {

    @Schema(description = "Numero da rodada", example = "15")
    private final int rodada;

    @Schema(
        description = "Aviso sobre o status do mercado. "
                    + "Preenchido quando o mercado nao esta aberto. "
                    + "Null quando o mercado esta aberto normalmente.",
        example     = "Mercado fechado. Rodada em andamento.",
        nullable    = true
    )
    private final String avisoMercado;

    @Schema(description = "Posicao filtrada. 'TODAS' quando sem filtro",
            example = "ATA", allowableValues = {"TODAS", "GOL", "LAT", "ZAG", "MEI", "ATA", "TEC"})
    private final String posicao;

    @Schema(description = "Limite de resultados retornados", example = "25")
    private final int limite;

    @Schema(description = "Total de atletas no pool antes de aplicar o limite", example = "47")
    private final int totalDisponivel;

    @Schema(description = "Lista de atletas ordenada por score decrescente")
    private final List<AtletaRankingDto> atletas;

    @Getter
    @Builder
    @Schema(description = "Atleta no ranking com posicao no ranking")
    public static class AtletaRankingDto {

        @Schema(description = "Posicao no ranking (1 = melhor score)", example = "1")
        private final int rank;

        @Schema(description = "Nome popular do atleta", example = "Hulk")
        private final String apelido;

        @Schema(description = "Formato 'Nome (SIG)' com aviso de duvida quando aplicavel",
                example = "Hulk (ATM)")
        private final String formatado;

        @Schema(description = "Sigla do clube", example = "ATM")
        private final String siglaClube;

        @Schema(description = "Nome completo do clube", example = "Atletico Mineiro")
        private final String nomeClube;

        @Schema(description = "Posicao do atleta",
                allowableValues = {"GOL", "LAT", "ZAG", "MEI", "ATA", "TEC"}, example = "ATA")
        private final String posicao;

        @Schema(description = "Status de disponibilidade", example = "Provavel")
        private final String status;

        @Schema(description = "Media de pontos na temporada", example = "9.5")
        private final double mediaPontos;

        @Schema(description = "Valorizacao na ultima rodada (C$)", example = "3.2")
        private final double valorizacao;

        @Schema(description = "Preco atual em cartoletas (C$)", example = "22.0")
        private final double preco;

        @Schema(description = "Score calculado pelo sistema", example = "8.5400")
        private final double score;

        @Schema(description = "Desvio padrao das pontuacoes nas ultimas rodadas consideradas. "
                            + "0.0 quando ha menos de 2 rodadas ou sem historico recente.",
                example = "1.2500")
        private final double desvioPadrao;

        @Schema(description = "Quantidade de rodadas com pontuacao usadas no calculo do desempenho. "
                            + "0 quando o atleta nao possui historico recente.",
                example = "5")
        private final int rodadasConsideradas;

        @Schema(description = "Indica se o atleta esta em duvida para a partida", example = "false")
        private final boolean emDuvida;

        public static AtletaRankingDto from(Atleta atleta, int rank) {
            return AtletaRankingDto.builder()
                    .rank(rank)
                    .apelido(atleta.getApelido())
                    .formatado(atleta.formatado())
                    .siglaClube(atleta.getSiglaClube())
                    .nomeClube(atleta.getNomeClube())
                    .posicao(atleta.getPosicao().name())
                    .status(atleta.getStatus().getLabel())
                    .mediaPontos(atleta.getMediaPontos())
                    .valorizacao(atleta.getValorizacao())
                    .preco(atleta.getPreco())
                    .score(atleta.getScore())
                    .desvioPadrao(atleta.getDesvioPadrao())
                    .rodadasConsideradas(atleta.getRodadasConsideradas())
                    .emDuvida(atleta.isDuvida())
                    .build();
        }
    }
}
