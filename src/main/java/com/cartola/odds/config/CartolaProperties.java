package com.cartola.odds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cartola.api")
public class CartolaProperties {

    private String baseUrl;
    private int timeout = 15000;
}
