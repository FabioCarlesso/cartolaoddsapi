package com.cartola.odds.model.response;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Usuario como a API o devolve.
 *
 * <p>Nao existe campo de senha aqui, nem em hash: a entidade {@code Usuario} nunca e
 * serializada direto na resposta justamente para que o hash BCrypt nao vaze por descuido
 * ao se adicionar um campo novo.
 */
@Getter
@Builder
@Schema(description = "Usuario cadastrado")
public class UsuarioResponse {

    @Schema(description = "Id do usuario", example = "1")
    private final Long id;

    @Schema(description = "Nome do usuario", example = "Fabio Carlesso")
    private final String nome;

    @Schema(description = "E-mail usado no login", example = "fabio@cartolaodds.local")
    private final String email;

    @Schema(description = "Perfil de acesso", example = "USER")
    private final Perfil perfil;

    @Schema(description = "Situacao do usuario; false impede o login", example = "true")
    private final boolean ativo;

    @Schema(description = "Data e hora do cadastro")
    private final LocalDateTime criadoEm;

    public static UsuarioResponse from(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .perfil(usuario.getPerfil())
                .ativo(usuario.isAtivo())
                .criadoEm(usuario.getCriadoEm())
                .build();
    }
}
