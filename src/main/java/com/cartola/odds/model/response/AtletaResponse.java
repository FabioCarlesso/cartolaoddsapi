package com.cartola.odds.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AtletaResponse {

    @JsonProperty("atletas")
    private List<AtletaItem> atletas;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtletaItem {

        @JsonProperty("atleta_id")
        private int atletaId;

        @JsonProperty("apelido")
        private String apelido;

        @JsonProperty("posicao_id")
        private int posicaoId;

        @JsonProperty("clube_id")
        private int clubeId;

        @JsonProperty("status_id")
        private int statusId;

        @JsonProperty("media_num")
        private Double mediaNum;

        @JsonProperty("variacao_num")
        private Double variacaoNum;

        @JsonProperty("preco_num")
        private Double precoNum;
    }
}
