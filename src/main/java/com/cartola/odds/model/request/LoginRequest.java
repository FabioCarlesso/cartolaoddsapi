package com.cartola.odds.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Credenciais de acesso")
public class LoginRequest {

    @NotBlank(message = "email e obrigatorio")
    @Email(message = "email deve ser um endereco valido")
    @Schema(description = "E-mail do usuario", example = "admin@cartolaodds.local")
    private String email;

    @NotBlank(message = "senha e obrigatoria")
    @Schema(description = "Senha do usuario", example = "troque-esta-senha")
    private String senha;
}
