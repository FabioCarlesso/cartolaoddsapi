package com.cartola.odds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.login")
public class LoginProperties {

    /** Tentativas malsucedidas toleradas por e-mail dentro da janela. */
    private int maxTentativas = 5;

    /** Janela do freio, em minutos, contada a partir da primeira falha. */
    private int janelaMinutos = 5;
}
