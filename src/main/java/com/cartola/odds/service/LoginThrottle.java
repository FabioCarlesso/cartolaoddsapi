package com.cartola.odds.service;

import com.cartola.odds.config.LoginProperties;
import com.cartola.odds.exception.TentativasExcedidasException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Freio de forca bruta no login: conta as falhas por e-mail e recusa novas tentativas
 * quando o limite da janela e atingido.
 *
 * <p>A chave e o e-mail, e nao o IP, de proposito. Em producao a aplicacao fica atras do
 * nginx e da borda da plataforma, entao {@code getRemoteAddr()} devolve o endereco do
 * proxy — igual para todo mundo. Contar por ali ou nao limitaria nada, ou limitaria todos
 * juntos. Ler {@code X-Forwarded-For} com seguranca exige saber quantos saltos confiar, o
 * que e configuracao do ambiente e chega com o deploy (#39). O e-mail, por outro lado,
 * descreve exatamente o alvo do ataque: quem tenta adivinhar a senha do administrador
 * martela sempre o mesmo endereco.
 *
 * <p>A janela conta a partir da primeira falha e o contador some inteiro ao expirar, sem
 * deslizar. O tamanho e limitado para que uma enumeracao de e-mails nao vire consumo de
 * memoria.
 */
@Slf4j
@Component
public class LoginThrottle {

    private static final int MAX_CHAVES = 10_000;

    private final Cache<String, AtomicInteger> falhasPorEmail;
    private final int maxTentativas;
    private final int janelaMinutos;

    public LoginThrottle(LoginProperties props) {
        this.maxTentativas = props.getMaxTentativas();
        this.janelaMinutos = props.getJanelaMinutos();
        this.falhasPorEmail = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(janelaMinutos))
                .maximumSize(MAX_CHAVES)
                .build();
    }

    /** Lanca {@link TentativasExcedidasException} quando o e-mail ja estourou a janela. */
    public void verificar(String email) {
        var falhas = falhasPorEmail.getIfPresent(chave(email));
        if (falhas != null && falhas.get() >= maxTentativas) {
            log.warn("Login bloqueado por excesso de tentativas: {}", email);
            throw new TentativasExcedidasException(
                    "Muitas tentativas de login. Tente novamente em ate %d minutos."
                            .formatted(janelaMinutos));
        }
    }

    public void registrarFalha(String email) {
        falhasPorEmail.get(chave(email), chave -> new AtomicInteger()).incrementAndGet();
    }

    /** Autenticacao bem-sucedida zera o contador do e-mail. */
    public void registrarSucesso(String email) {
        falhasPorEmail.invalidate(chave(email));
    }

    /**
     * E-mail normalizado: o login e case-insensitive
     * ({@code UsuarioRepository.findByEmailIgnoreCase}), e sem normalizar aqui bastaria
     * alternar a caixa para ganhar uma janela nova a cada tentativa.
     */
    private String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
