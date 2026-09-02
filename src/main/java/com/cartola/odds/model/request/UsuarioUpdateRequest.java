package com.cartola.odds.model.request;

import com.cartola.odds.model.enums.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Campos alteraveis de um usuario. Todos opcionais — o que vier {@code null} fica como esta.
 * A senha nao entra aqui: o proprio usuario a troca em {@code PATCH /api/usuarios/me/senha}.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Campos a atualizar no usuario. Todos sao opcionais — envie apenas o que deseja alterar.")
public class UsuarioUpdateRequest {

    @Size(min = 1, max = 120, message = "nome deve ter entre 1 e 120 caracteres")
    @Schema(description = "Nome do usuario", example = "Fabio Carlesso")
    private String nome;

    @Email(message = "email deve ser um endereco valido")
    @Size(max = 180, message = "email deve ter no maximo 180 caracteres")
    @Schema(description = "E-mail usado no login", example = "fabio@cartolaodds.local")
    private String email;

    @Schema(description = "Perfil de acesso", example = "ADMIN")
    private Perfil perfil;

    @Schema(description = "Situacao do usuario; false impede o login e derruba os tokens ja emitidos", example = "true")
    private Boolean ativo;
}
