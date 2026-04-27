package com.cartola.odds.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Relatorio de pagamento dos profissionais (atletas) escalados na rodada.
 * O "pagamento" representa o custo em cartoletas (C$) pago pelo gestor do time
 * para escalar cada atleta — analogo ao salario do profissional.
 */
@Getter
@Builder
public class RelatorioPagamento {

    private final int             rodada;
    private final String          avisoMercado;
    private final LocalDateTime   geradoEm;
    private final String          moeda;
    private final int             totalProfissionais;
    private final double          totalPagamento;
    private final List<ItemPagamento> itens;
}
