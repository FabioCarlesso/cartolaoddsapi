package com.cartola.odds.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Troca de senha do usuario autenticado")
public class AlterarSenhaRequest {

    @NotBlank(message = "senhaAtual e obrigatoria")
    @Schema(description = "Senha em uso hoje, exigida como confirmacao de identidade", example = "senha-atual-123")
    private String senhaAtual;

    @NotBlank(message = "novaSenha e obrigatoria")
    @Size(min = 8, max = 72, message = "novaSenha deve ter entre 8 e 72 caracteres")
    @Schema(description = "Nova senha (minimo de 8 caracteres)", example = "senha-nova-456")
    private String novaSenha;
}
