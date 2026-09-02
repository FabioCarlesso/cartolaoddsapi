package com.cartola.odds.model.response;

import com.cartola.odds.model.enums.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Access token emitido no login")
public class LoginResponse {

    @Schema(description = "Access token JWT, usado no header Authorization")
    private final String accessToken;

    @Schema(description = "Tipo do token", example = "Bearer")
    private final String tipo;

    /**
     * Tempo de vida restante, e nao um instante absoluto: o container roda em UTC e um
     * horario sem fuso seria lido como local pelo cliente, que passaria a achar que a
     * sessao dura horas a mais do que o token vale. Com a duracao, cada cliente conta a
     * partir do proprio relogio. Mesma escolha do {@code expires_in} do OAuth 2.
     */
    @Schema(description = "Tempo de vida do token em segundos, contado a partir da resposta", example = "86400")
    private final long expiraEmSegundos;

    @Schema(description = "Nome do usuario autenticado", example = "Fabio")
    private final String nome;

    @Schema(description = "Perfil de acesso do usuario", example = "ADMIN")
    private final Perfil perfil;
}
