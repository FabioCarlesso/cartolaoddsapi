package com.cartola.odds.model.response;

import com.cartola.odds.model.ItemPagamento;
import com.cartola.odds.model.RelatorioPagamento;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato amigavel ao Angular para o relatorio de pagamento dos profissionais.
 * Campos sempre presentes (nullable explicito), datas em ISO-8601, valores numericos
 * primitivos para facilitar binding em formularios reativos e tabelas Material.
 */
@Getter
@Builder
@Schema(description = "Relatorio de pagamento dos profissionais escalados na rodada")
public class RelatorioPagamentoResponse {

    @Schema(description = "Numero da rodada de referencia", example = "15")
    private final int rodada;

    @Schema(
        description = "Aviso sobre o status do mercado. Null quando o mercado esta aberto.",
        example     = "Mercado fechado. Rodada em andamento.",
        nullable    = true
    )
    private final String avisoMercado;

    @Schema(description = "Data e hora ISO-8601 em que o relatorio foi gerado",
            example = "2025-04-27T15:30:00")
    private final LocalDateTime geradoEm;

    @Schema(description = "Moeda dos valores apresentados", example = "C$")
    private final String moeda;

    @Schema(description = "Total de profissionais incluidos no relatorio (titulares + reservas)",
            example = "19")
    private final int totalProfissionais;

    @Schema(description = "Soma total dos pagamentos (em cartoletas)", example = "142.5")
    private final double totalPagamento;

    @Schema(description = "Lista de pagamentos por profissional, ordenada por valor decrescente")
    private final List<ItemPagamentoResponse> itens;

    public static RelatorioPagamentoResponse from(RelatorioPagamento relatorio) {
        return RelatorioPagamentoResponse.builder()
                .rodada(relatorio.getRodada())
                .avisoMercado(relatorio.getAvisoMercado())
                .geradoEm(relatorio.getGeradoEm())
                .moeda(relatorio.getMoeda())
                .totalProfissionais(relatorio.getTotalProfissionais())
                .totalPagamento(relatorio.getTotalPagamento())
                .itens(relatorio.getItens().stream().map(ItemPagamentoResponse::from).toList())
                .build();
    }

    @Getter
    @Builder
    @Schema(description = "Pagamento de um profissional escalado")
    public static class ItemPagamentoResponse {

        @Schema(description = "Identificador interno do atleta (Cartola)", example = "82934")
        private final int atletaId;

        @Schema(description = "Nome popular do profissional", example = "Hulk")
        private final String apelido;

        @Schema(description = "Sigla da posicao", allowableValues = {"GOL", "LAT", "ZAG", "MEI", "ATA", "TEC"},
                example = "ATA")
        private final String posicao;

        @Schema(description = "Descricao da posicao", example = "Atacante")
        private final String posicaoLabel;

        @Schema(description = "Sigla do clube", example = "ATM")
        private final String siglaClube;

        @Schema(description = "Nome completo do clube", example = "Atletico Mineiro")
        private final String nomeClube;

        @Schema(description = "Status atual do profissional", example = "Provavel")
        private final String status;

        @Schema(description = "Funcao no time: Capitao, Titular ou Reserva", example = "Titular",
                allowableValues = {"Capitao", "Titular", "Reserva"})
        private final String funcao;

        @Schema(description = "Indica se o profissional e titular", example = "true")
        private final boolean titular;

        @Schema(description = "Indica se o profissional e o capitao", example = "false")
        private final boolean capitao;

        @Schema(description = "Valor do pagamento (cartoletas)", example = "22.0")
        private final double valorPagamento;

        public static ItemPagamentoResponse from(ItemPagamento item) {
            return ItemPagamentoResponse.builder()
                    .atletaId(item.getAtletaId())
                    .apelido(item.getApelido())
                    .posicao(item.getPosicao() != null ? item.getPosicao().name() : null)
                    .posicaoLabel(item.getPosicaoLabel())
                    .siglaClube(item.getSiglaClube())
                    .nomeClube(item.getNomeClube())
                    .status(item.getStatusLabel())
                    .funcao(item.getFuncao())
                    .titular(item.isTitular())
                    .capitao(item.isCapitao())
                    .valorPagamento(item.getValorPagamento())
                    .build();
        }
    }
}
