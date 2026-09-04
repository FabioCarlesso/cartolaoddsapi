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
 * Freio de forca bruta na conferencia de senha: conta as falhas por e-mail e recusa novas
 * tentativas quando o limite da janela e atingido.
 *
 * <p>Serve ao login e a troca de senha, que compartilham o mesmo contador de proposito:
 * as duas conferem o mesmo segredo, e separa-las daria ao atacante duas janelas para
 * adivinhar a mesma senha.
 *
 * <p>A chave e o e-mail, e nao o IP, de proposito — e nao por falta de IP confiavel. Com
 * {@code server.forward-headers-strategy=native} o {@code RemoteIpValve} reescreve
 * {@code getRemoteAddr()} a partir do {@code X-Forwarded-For} quando a conexao vem de um
 * proxy confiavel ({@code server.tomcat.remoteip.internal-proxies}), entao a aplicacao
 * enxerga o endereco real de quem chamou e contar por IP seria viavel.
 *
 * <p>Continua nao sendo o que se quer contar. O e-mail descreve o alvo do ataque; o IP
 * descreve so o caminho, e caminho e o que um atacante distribuido troca de graca — quem
 * martela a senha do administrador martela sempre o mesmo endereco, de onde quer que venha.
 * Contar por IP tambem faria o freio depender de uma faixa de proxies bem configurada para
 * nao punir todo mundo junto atras de um NAT.
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
            log.warn("Conferencia de senha bloqueada por excesso de tentativas: {}", email);
            throw new TentativasExcedidasException(
                    "Muitas tentativas malsucedidas. Tente novamente em ate %d minutos."
                            .formatted(janelaMinutos));
        }
    }

    public void registrarFalha(String email) {
        falhasPorEmail.get(chave(email), chave -> new AtomicInteger()).incrementAndGet();
    }

    /** Conferencia bem-sucedida zera o contador do e-mail. */
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
