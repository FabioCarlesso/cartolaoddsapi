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
public class PartidaResponse {

    @JsonProperty("partidas")
    private List<PartidaItem> partidas;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PartidaItem {

        @JsonProperty("clube_casa_id")
        private int clubeCasaId;

        @JsonProperty("clube_visitante_id")
        private int clubeVisitanteId;
    }
}
