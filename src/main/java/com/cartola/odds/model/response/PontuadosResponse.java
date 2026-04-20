package com.cartola.odds.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * DTO da resposta do endpoint GET /atletas/pontuados da API do Cartola FC.
 *
 * O endpoint retorna um mapa de atleta_id -> dados de pontuacao da rodada.
 * Cada AtletaPontuado contem a pontuacao real e os scouts da partida.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PontuadosResponse {

    /** Mapa de atleta_id (como String) -> dados de pontuacao */
    @JsonProperty("atletas")
    private Map<String, AtletaPontuado> atletas;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AtletaPontuado {

        @JsonProperty("atleta_id")
        private int atletaId;

        @JsonProperty("apelido")
        private String apelido;

        @JsonProperty("pontuacao")
        private Double pontuacao;

        @JsonProperty("pontuacao_num")
        private Double pontuacaoNum;

        @JsonProperty("rodada_id")
        private Integer rodadaId;
    }
}
