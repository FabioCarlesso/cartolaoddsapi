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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminInicialBootstrap")
class AdminInicialBootstrapTest {

    @Mock UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminInicialBootstrap bootstrap(String senha, String... perfis) {
        var props = new AdminInicialProperties();
        props.setEmail("admin@cartolaodds.local");
        props.setSenha(senha);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(perfis);
        return new AdminInicialBootstrap(props, usuarioRepository, passwordEncoder, environment);
    }

    @Test
    @DisplayName("deve criar o admin inicial quando nao existe ADMIN ativo")
    void deveCriarAdminInicial() {
        when(usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        bootstrap("senha-forte-123").run(null);

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
        when(usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        bootstrap("senha-forte-123").run(null);

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

        bootstrap("senha-forte-123").run(null);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve falhar ao iniciar em producao sem APP_ADMIN_INICIAL_SENHA")
    void deveFalharEmProducaoSemSenha() {
        assertThatThrownBy(() -> bootstrap(null, "prod").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_INICIAL_SENHA nao configurada");
    }

    @Test
    @DisplayName("deve seguir sem criar admin fora de producao quando a senha esta ausente")
    void deveSeguirSemCriarForaDeProducao() {
        when(usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)).thenReturn(false);

        bootstrap(" ").run(null);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve falhar quando a senha do admin inicial e curta demais")
    void deveFalharComSenhaCurta() {
        assertThatThrownBy(() -> bootstrap("curta").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimo e 8");
    }
}
