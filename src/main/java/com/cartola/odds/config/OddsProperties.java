package com.cartola.odds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "odds.api")
public class OddsProperties {

    private String key;
    private String baseUrl;
    private String sport;
    private String regions;
    private String markets;
    private int timeout = 10000;
}
