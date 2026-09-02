package com.cartola.odds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.admin-inicial")
public class AdminInicialProperties {

    /** E-mail do administrador criado no primeiro boot. */
    private String email = "admin@cartolaodds.local";

    /**
     * Senha do administrador inicial. Sem padrao versionado: em producao e obrigatoria
     * (APP_ADMIN_INICIAL_SENHA) e, fora dela, quando ausente, nenhum admin e criado.
     */
    private String senha;
}
