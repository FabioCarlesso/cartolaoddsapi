package com.cartola.odds.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cartola FC - Odds API")
                        .description("""
                                API REST que monta automaticamente um time competitivo para o Cartola FC
                                cruzando odds do Brasileirao com metricas dos atletas da plataforma.

                                **Fluxo do pipeline:**
                                1. Verifica status do mercado Cartola
                                2. Busca odds e identifica times favoritos (odd <= ODD_LIMITE)
                                3. Filtra atletas por status (Provavel/Duvida), preco e time favorito
                                4. Calcula score ponderado de cada atleta
                                5. Monta time na formacao 4-3-3 (1 GOL, 2 LAT, 2 ZAG, 3 MEI, 3 ATA, 1 TEC)
                                6. Define capitao, reserva de luxo e substitutos para duvidas

                                **Autenticacao:** todos os endpoints exigem um access token JWT.
                                Obtenha o seu em `POST /api/auth/login` e informe-o no botao
                                **Authorize** acima, colando apenas o valor do `accessToken`.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Cartola Odds")
                                .email("contato@cartola-odds.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")
                ))
                // Aplica o cadeado a todas as operacoes; o proprio /api/auth/login e publico,
                // e um token invalido ali simplesmente e ignorado.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .name(ESQUEMA_JWT)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token devolvido por POST /api/auth/login")));
    }
}
