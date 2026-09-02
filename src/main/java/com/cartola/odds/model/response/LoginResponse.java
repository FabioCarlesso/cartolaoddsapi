package com.cartola.odds.model.response;

import com.cartola.odds.model.enums.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Access token emitido no login")
public class LoginResponse {

    @Schema(description = "Access token JWT, usado no header Authorization")
    private final String accessToken;

    @Schema(description = "Tipo do token", example = "Bearer")
    private final String tipo;

    @Schema(description = "Instante em que o token expira")
    private final LocalDateTime expiraEm;

    @Schema(description = "Nome do usuario autenticado", example = "Fabio")
    private final String nome;

    @Schema(description = "Perfil de acesso do usuario", example = "ADMIN")
    private final Perfil perfil;
}
