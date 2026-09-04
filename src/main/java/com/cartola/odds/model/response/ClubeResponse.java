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

    @JsonProperty("nome_fantasia")
    private String nomeFantasia;

    /**
     * Identificador estavel do clube ("mirassol", "atletico-pr"). E o unico campo textual
     * que ainda traz o nome por extenso: desde 2026 o {@code /partidas} devolve a sigla em
     * {@code nome}, {@code abreviacao} e {@code nome_fantasia} (todos "MIR"), e {@code apelido}
     * carrega o apelido de torcida ("Mirassol", mas tambem "Colorado" e "Furacao"). E por isso
     * a fonte da chave de cruzamento com a The Odds API.
     */
    @JsonProperty("slug")
    private String slug;
}
