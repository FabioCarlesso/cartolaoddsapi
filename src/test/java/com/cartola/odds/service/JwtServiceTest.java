package com.cartola.odds.service;

import com.cartola.odds.config.JwtProperties;
import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres";
    private static final String OUTRO_SEGREDO = "outro-segredo-de-teste-com-mais-de-32-chars";

    private static JwtService jwtService(String secret, long expirationMs, String... perfis) {
        var props = new JwtProperties();
        props.setSecret(secret);
        props.setExpirationMs(expirationMs);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(perfis);
        return new JwtService(props, environment);
    }

    private static Usuario usuario() {
        var usuario = new Usuario();
        usuario.setId(7L);
        usuario.setNome("Fabio");
        usuario.setEmail("fabio@cartolaodds.local");
        usuario.setSenha("$2a$10$hash");
        usuario.setPerfil(Perfil.ADMIN);
        usuario.setTokenVersion(3L);
        return usuario;
    }

    @Test
    @DisplayName("deve emitir token com email no subject")
    void deveEmitirTokenComEmailNoSubject() {
        var service = jwtService(SEGREDO, 60_000L);

        var token = service.gerarToken(usuario());

        assertThat(service.extrairEmail(token)).isEqualTo("fabio@cartolaodds.local");
    }

    @Test
    @DisplayName("deve incluir as claims perfil, usuarioId e tokenVersion")
    void deveIncluirClaims() {
        var service = jwtService(SEGREDO, 60_000L);

        var claims = service.lerClaims(service.gerarToken(usuario()));

        assertThat(claims.get(JwtService.CLAIM_PERFIL)).isEqualTo("ADMIN");
        assertThat(((Number) claims.get(JwtService.CLAIM_USUARIO_ID)).longValue()).isEqualTo(7L);
        assertThat(((Number) claims.get(JwtService.CLAIM_TOKEN_VERSION)).longValue()).isEqualTo(3L);
    }

    @Test
    @DisplayName("deve extrair tokenVersion do token")
    void deveExtrairTokenVersion() {
        var service = jwtService(SEGREDO, 60_000L);

        assertThat(service.extrairTokenVersion(service.gerarToken(usuario()))).isEqualTo(3L);
    }

    @Test
    @DisplayName("deve recusar token expirado")
    void deveRecusarTokenExpirado() {
        var service = jwtService(SEGREDO, -1_000L);
        var token = service.gerarToken(usuario());

        assertThatThrownBy(() -> service.lerClaims(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("deve recusar token assinado com outra chave")
    void deveRecusarTokenDeOutraChave() {
        var emissor = jwtService(OUTRO_SEGREDO, 60_000L);
        var validador = jwtService(SEGREDO, 60_000L);
        var token = emissor.gerarToken(usuario());

        assertThatThrownBy(() -> validador.lerClaims(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("deve falhar quando o segredo tem menos de 32 caracteres")
    void deveFalharComSegredoCurto() {
        assertThatThrownBy(() -> jwtService("curto-demais", 60_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("deve falhar ao subir em producao sem segredo configurado")
    void deveFalharEmProducaoSemSegredo() {
        assertThatThrownBy(() -> jwtService(null, 60_000L, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET nao configurado");
    }

    @Test
    @DisplayName("deve gerar chave efemera fora de producao quando o segredo esta ausente")
    void deveGerarChaveEfemeraForaDeProducao() {
        assertThatCode(() -> jwtService("  ", 60_000L).gerarToken(usuario())).doesNotThrowAnyException();
    }
}
