package com.cartola.odds.controller.api;

import com.cartola.odds.model.request.AlterarSenhaRequest;
import com.cartola.odds.model.request.UsuarioRequest;
import com.cartola.odds.model.request.UsuarioUpdateRequest;
import com.cartola.odds.model.response.ErrorResponse;
import com.cartola.odds.model.response.PaginaResponse;
import com.cartola.odds.model.response.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Contrato REST da gestao de usuarios.
 * Toda a documentacao Swagger fica aqui — a implementacao fica limpa.
 */
@Tag(name = "Usuarios", description = "Cadastro e manutencao de usuarios (restrito a ADMIN) e dados da propria conta")
@RequestMapping("/api/usuarios")
public interface UsuarioApi {

    @PostMapping
    @Operation(
        summary     = "Criar usuario",
        description = """
            **Restrito a `ADMIN`.** Cria um usuario que ja consegue autenticar em
            `POST /api/auth/login` com a senha informada.

            Nao ha auto-cadastro publico: todo acesso a esta API nasce de um administrador.

            - `perfil` e opcional — sem ele o usuario nasce como `USER`.
            - A senha e gravada em hash BCrypt e nunca volta em nenhuma resposta.
            - O e-mail e normalizado para minusculas e precisa ser unico.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario criado",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
        @ApiResponse(responseCode = "400", description = "Payload invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nao autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Autenticado, mas sem perfil ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "E-mail ja cadastrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request);

    @GetMapping
    @Operation(
        summary     = "Listar usuarios",
        description = """
            **Restrito a `ADMIN`.** Lista paginada de todos os usuarios, ativos e inativos.

            Aceita os parametros padrao de paginacao (`page`, `size`, `sort`); sem eles,
            devolve os 20 primeiros ordenados por nome. A senha nao aparece na resposta,
            nem em hash.

            `sort` aceita apenas `id`, `nome`, `email`, `perfil`, `ativo` e `criadoEm`;
            qualquer outro campo responde `400`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pagina de usuarios",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class))),
        @ApiResponse(responseCode = "400", description = "Campo de ordenacao nao suportado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nao autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Autenticado, mas sem perfil ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<PaginaResponse<UsuarioResponse>> listar(
            @ParameterObject @PageableDefault(size = 20, sort = "nome") Pageable pageable);

    @GetMapping("/me")
    @Operation(
        summary     = "Dados da propria conta",
        description = """
            Devolve o usuario dono do token da requisicao. Disponivel para qualquer
            usuario autenticado, sem exigir perfil `ADMIN`.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario autenticado",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nao autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UsuarioResponse> buscarLogado();

    @PatchMapping("/me/senha")
    @Operation(
        summary     = "Trocar a propria senha",
        description = """
            Troca a senha do usuario autenticado, exigindo a senha atual como confirmacao
            de identidade — um token roubado nao basta para tomar a conta.

            **A troca derruba todos os tokens ja emitidos** para o usuario, inclusive o
            usado nesta chamada: a `tokenVersion` e incrementada e a proxima requisicao com
            o token antigo responde `401`. E preciso autenticar de novo com a senha nova.

            **Freio de forca bruta:** a conferencia da senha atual usa o mesmo contador do
            login. Tentativas malsucedidas seguidas passam a receber `429` ate a janela
            expirar — um token roubado nao vira tentativas ilimitadas de adivinhar a senha.

            Nao ha recuperacao de senha por e-mail nesta versao: quem esquece a senha
            depende do administrador da instancia.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Senha alterada; tokens anteriores invalidados"),
        @ApiResponse(responseCode = "400", description = "Payload invalido (nova senha com menos de 8 caracteres)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nao autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Senha atual incorreta",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Excesso de tentativas com a senha atual errada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> alterarSenha(@Valid @RequestBody AlterarSenhaRequest request);

    @GetMapping("/{id}")
    @Operation(
        summary     = "Detalhar usuario",
        description = "**Restrito a `ADMIN`.** Retorna um usuario pelo id."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nao autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Autenticado, mas sem perfil ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable Long id);

    @PatchMapping("/{id}")
    @Operation(
        summary     = "Atualizar usuario",
        description = """
            **Restrito a `ADMIN`.** Atualiza `nome`, `email`, `perfil` e `ativo`. Todos os
            campos sao opcionais — envie apenas o que deseja alterar. A senha nao entra
            aqui: o proprio usuario a troca em `PATCH /api/usuarios/me/senha`.

            **Efeito nos tokens:** rebaixar o perfil ou desativar o usuario incrementa a
            `tokenVersion` e derruba os tokens ja emitidos para ele. Trocar o e-mail tem o
            mesmo efeito na pratica — o e-mail e o `subject` do token. Vale inclusive para
            o administrador que troca o **proprio** e-mail: ele precisa autenticar de novo
            logo apos a chamada.

            **Protecoes:** um administrador nao pode desativar nem rebaixar a propria conta,
            e a API recusa (`409`) a operacao que deixaria a instancia sem nenhum
            administrador ativo.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario atualizado",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class))),
        @ApiResponse(responseCode = "400", description = "Payload invalido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Nao autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Autenticado, mas sem perfil ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "E-mail ja cadastrado, propria conta ou ultimo administrador ativo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<UsuarioResponse> atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateRequest request);

    @DeleteMapping("/{id}")
    @Operation(
        summary     = "Desativar usuario",
        description = """
            **Restrito a `ADMIN`.** Desativacao **logica**: marca `ativo = false` e o
            registro continua no banco, para nao apagar o historico de quem o produziu. O
            usuario deixa de autenticar e os tokens ja emitidos para ele param de valer.

            Repetir a chamada sobre um usuario ja inativo responde `204` sem alterar nada.

            **Protecoes:** um administrador nao pode desativar a propria conta, e a API
            recusa (`409`) desativar o ultimo administrador ativo.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Usuario desativado"),
        @ApiResponse(responseCode = "401", description = "Nao autenticado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Autenticado, mas sem perfil ADMIN",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Usuario inexistente",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Propria conta ou ultimo administrador ativo",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Void> desativar(@PathVariable Long id);
}
