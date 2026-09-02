package com.cartola.odds.controller.api;

import com.cartola.odds.model.request.LoginRequest;
import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrato REST da autenticacao.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Autenticacao", description = "Login e emissao de access token JWT")
@RequestMapping("/api/auth")
public interface AuthApi {

    @PostMapping("/login")
    @Operation(
        summary     = "Login",
        description = """
            Endpoint publico. Valida e-mail e senha e devolve o access token JWT.

            **Como usar o token:**
            - Nas chamadas a API: header `Authorization: Bearer <accessToken>`
            - No Swagger UI: botao **Authorize**, colando apenas o valor do `accessToken`

            O campo `expiraEmSegundos` diz por quanto tempo o token vale a partir da
            resposta — o cliente conta pelo proprio relogio, sem depender do fuso do
            servidor. O token deixa de valer antes disso se a senha do usuario for trocada
            ou se ele for desativado.

            **Freio de forca bruta:** tentativas malsucedidas seguidas para o mesmo e-mail
            passam a receber `429` ate a janela expirar (`APP_LOGIN_MAX_TENTATIVAS` e
            `APP_LOGIN_JANELA_MINUTOS`). Um login bem-sucedido zera a contagem.

            **A criacao de usuarios nao e publica** — ela e feita por um administrador.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Autenticado com sucesso",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Payload invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Credenciais invalidas ou usuario inativo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Excesso de tentativas de login para o e-mail informado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request);
}
