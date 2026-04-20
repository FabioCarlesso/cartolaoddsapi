package com.cartola.odds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CartolaOddsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartolaOddsApplication.class, args);
    }
}
