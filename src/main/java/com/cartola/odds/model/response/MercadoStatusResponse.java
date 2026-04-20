package com.cartola.odds.model.response;

import com.cartola.odds.model.enums.StatusMercado;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO da resposta do endpoint GET /mercado/status da API do Cartola FC.
 *
 * Codigos de status_mercado conhecidos:
 *  1 - Aberto      : mercado aberto para escalacao
 *  2 - Fechado     : mercado fechado (rodada em andamento)
 *  3 - Manutencao  : pre-temporada ou manutencao
 *  4 - Parcial     : alguns jogos ja ocorreram
 *  6 - Finalizando : processamento pos-rodada
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MercadoStatusResponse {

    @JsonProperty("status_mercado")
    private int statusMercado;

    @JsonProperty("rodada_atual")
    private int rodadaAtual;

    /** Resolve o enum StatusMercado a partir do codigo numerico. */
    public StatusMercado getStatus() {
        return StatusMercado.fromCodigo(statusMercado);
    }

    /** Retorna true somente quando status_mercado == 1 (Aberto). */
    public boolean isAberto() {
        return getStatus().isAberto();
    }

    /** Retorna o aviso textual quando o mercado nao esta aberto, null caso contrario. */
    public String getAvisoMercado() {
        var status = getStatus();
        return status.isExibirAviso() ? status.getAviso() : null;
    }
}
