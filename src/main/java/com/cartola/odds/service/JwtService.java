package com.cartola.odds.service;

import com.cartola.odds.config.JwtProperties;
import com.cartola.odds.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emissao e leitura dos access tokens JWT, assinados com HMAC-SHA.
 *
 * <p>O algoritmo acompanha o tamanho do segredo: 32 caracteres dao HS256 e segredos
 * maiores sobem para HS384/HS512 — quem escolhe e o {@code Keys.hmacShaKeyFor}.
 *
 * <p>O token carrega o e-mail no {@code subject} e tres claims: {@code perfil},
 * {@code usuarioId} e {@code tokenVersion} — esta ultima e o que permite invalidar
 * tokens ja emitidos sem guardar sessao no servidor.
 */
@Slf4j
@Service
public class JwtService {

    /** Tamanho minimo para HMAC-SHA, correspondente ao HS256 (256 bits). */
    private static final int SECRET_MIN_CHARS = 32;

    public static final String CLAIM_PERFIL = "perfil";
    public static final String CLAIM_USUARIO_ID = "usuarioId";
    public static final String CLAIM_TOKEN_VERSION = "tokenVersion";

    private final SecretKey secretKey;
    private final JwtParser parser;
    private final long expirationMs;

    public JwtService(JwtProperties props, Environment environment) {
        this.secretKey = resolverChave(props.getSecret(), environment);
        this.parser = Jwts.parser().verifyWith(secretKey).build();
        this.expirationMs = props.getExpirationMs();
    }

    public String gerarToken(Usuario usuario) {
        var agora = Instant.now();
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(CLAIM_PERFIL, usuario.getPerfil().name())
                .claim(CLAIM_USUARIO_ID, usuario.getId())
                .claim(CLAIM_TOKEN_VERSION, usuario.getTokenVersion())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plusMillis(expirationMs)))
                .signWith(secretKey)
                .compact();
    }

    /** Lanca {@link io.jsonwebtoken.JwtException} para token invalido, expirado ou mal assinado. */
    public Claims lerClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public String extrairEmail(String token) {
        return lerClaims(token).getSubject();
    }

    public Long extrairTokenVersion(String token) {
        var valor = lerClaims(token).get(CLAIM_TOKEN_VERSION);
        return valor instanceof Number numero ? numero.longValue() : null;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /**
     * Segredo ausente e erro fatal em producao. Fora dela, gera uma chave aleatoria
     * para a aplicacao subir sem configuracao — ao custo de invalidar os tokens
     * emitidos antes do restart, o que o log avisa.
     */
    private static SecretKey resolverChave(String secret, Environment environment) {
        if (secret == null || secret.isBlank()) {
            if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "JWT_SECRET nao configurado. Em producao o segredo e obrigatorio "
                                + "(minimo de " + SECRET_MIN_CHARS + " caracteres).");
            }
            log.warn("JWT_SECRET nao configurado: usando chave efemera. "
                    + "Os tokens emitidos deixam de valer a cada restart da aplicacao.");
            return Jwts.SIG.HS256.key().build();
        }

        if (secret.length() < SECRET_MIN_CHARS) {
            throw new IllegalStateException(
                    "JWT_SECRET tem %d caracteres; o minimo para HS256 e %d."
                            .formatted(secret.length(), SECRET_MIN_CHARS));
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
