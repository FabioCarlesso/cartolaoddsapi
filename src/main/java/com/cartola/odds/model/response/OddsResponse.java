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
public class OddsResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("home_team")
    private String homeTeam;

    @JsonProperty("away_team")
    private String awayTeam;

    @JsonProperty("bookmakers")
    private List<Bookmaker> bookmakers;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Bookmaker {

        @JsonProperty("key")
        private String key;

        @JsonProperty("markets")
        private List<Market> markets;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Market {

        @JsonProperty("key")
        private String key;

        @JsonProperty("outcomes")
        private List<Outcome> outcomes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Outcome {

        @JsonProperty("name")
        private String name;

        @JsonProperty("price")
        private double price;
    }
}
