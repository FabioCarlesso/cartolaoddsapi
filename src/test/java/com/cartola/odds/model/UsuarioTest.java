package com.cartola.odds.model;

import com.cartola.odds.model.enums.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Usuario")
class UsuarioTest {

    private Usuario usuario(Perfil perfil, boolean ativo) {
        var usuario = new Usuario();
        usuario.setNome("Fabio");
        usuario.setEmail("fabio@cartolaodds.local");
        usuario.setSenha("$2a$10$hash");
        usuario.setPerfil(perfil);
        usuario.setAtivo(ativo);
        return usuario;
    }

    @Test
    @DisplayName("deve usar o e-mail como username do Spring Security")
    void deveUsarEmailComoUsername() {
        assertThat(usuario(Perfil.USER, true).getUsername()).isEqualTo("fabio@cartolaodds.local");
    }

    @Test
    @DisplayName("deve expor a authority com prefixo ROLE_")
    void deveExporAuthorityComPrefixo() {
        assertThat(usuario(Perfil.ADMIN, true).getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("deve refletir o campo ativo em isEnabled")
    void deveRefletirAtivoEmIsEnabled() {
        assertThat(usuario(Perfil.USER, true).isEnabled()).isTrue();
        assertThat(usuario(Perfil.USER, false).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("deve nascer com perfil USER, ativo e tokenVersion zerada")
    void deveTerPadroes() {
        var novo = new Usuario();

        assertThat(novo.getPerfil()).isEqualTo(Perfil.USER);
        assertThat(novo.isAtivo()).isTrue();
        assertThat(novo.getTokenVersion()).isZero();
    }

    @Test
    @DisplayName("deve incrementar a tokenVersion para invalidar tokens ja emitidos")
    void deveIncrementarTokenVersion() {
        var usuario = usuario(Perfil.USER, true);

        usuario.incrementarTokenVersion();
        usuario.incrementarTokenVersion();

        assertThat(usuario.getTokenVersion()).isEqualTo(2L);
    }
}
