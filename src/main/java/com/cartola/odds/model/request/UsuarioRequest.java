package com.cartola.odds.model.request;

import com.cartola.odds.model.enums.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Dados para cadastro de um novo usuario")
public class UsuarioRequest {

    @NotBlank(message = "nome e obrigatorio")
    @Size(max = 120, message = "nome deve ter no maximo 120 caracteres")
    @Schema(description = "Nome do usuario", example = "Fabio Carlesso")
    private String nome;

    @NotBlank(message = "email e obrigatorio")
    @Email(message = "email deve ser um endereco valido")
    @Size(max = 180, message = "email deve ter no maximo 180 caracteres")
    @Schema(description = "E-mail usado no login", example = "fabio@cartolaodds.local")
    private String email;

    @NotBlank(message = "senha e obrigatoria")
    @Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
    @Schema(description = "Senha inicial do usuario (minimo de 8 caracteres)", example = "senha-forte-123")
    private String senha;

    @Schema(description = "Perfil de acesso; quando ausente, o usuario nasce como USER", example = "USER")
    private Perfil perfil;
}
