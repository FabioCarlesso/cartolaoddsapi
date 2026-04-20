package com.cartola.odds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cartola")
public class AppProperties {

    private double oddLimite = 3.0;
    private Map<String, Integer> formacao;
    private Score score = new Score();

    @Getter
    @Setter
    public static class Score {
        private Peso peso = new Peso();

        @Getter
        @Setter
        public static class Peso {
            private double mediaPontos = 0.40;
            private double valorizacao = 0.20;
            private double desempenho  = 0.20;
            private double fatorCasa   = 0.10;
            private double timeFavorito = 0.10;
        }
    }
}
