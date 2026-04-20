package com.cartola.odds.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClubeResponse {

    @JsonProperty("nome")
    private String nome;

    @JsonProperty("abreviacao")
    private String abreviacao;
}
