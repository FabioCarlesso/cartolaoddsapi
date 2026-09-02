package com.cartola.odds.controller;

import com.cartola.odds.controller.api.UsuarioApi;
import com.cartola.odds.model.request.AlterarSenhaRequest;
import com.cartola.odds.model.request.UsuarioRequest;
import com.cartola.odds.model.request.UsuarioUpdateRequest;
import com.cartola.odds.model.response.PaginaResponse;
import com.cartola.odds.model.response.UsuarioResponse;
import com.cartola.odds.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Gestao de usuarios.
 *
 * <p>O {@code @PreAuthorize} fica aqui, ao lado do endpoint, e nao como matcher de URL no
 * {@code SecurityConfig}: as rotas de {@code /api/usuarios} misturam operacoes de
 * administrador com as do proprio usuario (`/me`), e duas fontes de verdade sobre quem
 * acessa o que so criariam a chance de divergirem.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UsuarioController implements UsuarioApi {

    private final UsuarioService usuarioService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> criar(UsuarioRequest request) {
        log.info("POST /api/usuarios - Criando usuario...");
        var criado = usuarioService.criar(request);

        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(criado.getId())
                .toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaginaResponse<UsuarioResponse>> listar(Pageable pageable) {
        log.info("GET /api/usuarios - page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(usuarioService.listar(pageable));
    }

    @Override
    public ResponseEntity<UsuarioResponse> buscarLogado() {
        log.info("GET /api/usuarios/me");
        return ResponseEntity.ok(usuarioService.buscarLogado());
    }

    @Override
    public ResponseEntity<Void> alterarSenha(AlterarSenhaRequest request) {
        log.info("PATCH /api/usuarios/me/senha");
        usuarioService.alterarSenha(request);
        return ResponseEntity.ok().build();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> buscarPorId(Long id) {
        log.info("GET /api/usuarios/{}", id);
        return ResponseEntity.ok(usuarioService.buscarPorId(id));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UsuarioResponse> atualizar(Long id, UsuarioUpdateRequest request) {
        log.info("PATCH /api/usuarios/{}", id);
        return ResponseEntity.ok(usuarioService.atualizar(id, request));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desativar(Long id) {
        log.info("DELETE /api/usuarios/{}", id);
        usuarioService.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
