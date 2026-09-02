package com.cartola.odds.config;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminInicialBootstrap")
class AdminInicialBootstrapTest {

    @Mock UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminInicialBootstrap bootstrap(String senha) {
        var props = new AdminInicialProperties();
        props.setEmail("admin@cartolaodds.local");
        props.setSenha(senha);
        return new AdminInicialBootstrap(props, usuarioRepository, passwordEncoder);
    }

    private void semAdminAtivo() {
        when(usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);
    }

    @Test
    @DisplayName("deve criar o admin inicial quando nao existe ADMIN ativo")
    void deveCriarAdminInicial() {
        semAdminAtivo();

        bootstrap("senha-forte-123").executar();

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        var salvo = captor.getValue();
        assertThat(salvo.getEmail()).isEqualTo("admin@cartolaodds.local");
        assertThat(salvo.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(salvo.isAtivo()).isTrue();
    }

    @Test
    @DisplayName("deve gravar a senha com hash BCrypt, nunca em claro")
    void deveGravarSenhaComHash() {
        semAdminAtivo();

        bootstrap("senha-forte-123").executar();

        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        var hash = captor.getValue().getSenha();
        assertThat(hash).isNotEqualTo("senha-forte-123").startsWith("$2");
        assertThat(passwordEncoder.matches("senha-forte-123", hash)).isTrue();
    }

    @Test
    @DisplayName("nao deve duplicar o admin quando ja existe ADMIN ativo")
    void naoDeveDuplicarAdmin() {
        when(usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(true);

        bootstrap("senha-forte-123").executar();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("nao deve exigir a senha quando ja existe ADMIN ativo, para nao manter segredo obsoleto no ambiente")
    void naoDeveExigirSenhaComAdminExistente() {
        when(usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(true);

        bootstrap(null).executar();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve falhar ao iniciar quando nao ha ADMIN ativo e a senha nao foi configurada")
    void deveFalharSemAdminESemSenha() {
        semAdminAtivo();

        assertThatThrownBy(() -> bootstrap(" ").executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_INICIAL_SENHA nao configurada");
    }

    @Test
    @DisplayName("deve falhar quando a senha do admin inicial e curta demais")
    void deveFalharComSenhaCurta() {
        semAdminAtivo();

        assertThatThrownBy(() -> bootstrap("curta").executar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimo e 8");
    }
}
