package com.cartola.odds.config;

import com.cartola.odds.security.ErroSegurancaHandler;
import com.cartola.odds.security.JwtAuthenticationFilter;
import com.cartola.odds.service.JwtService;
import com.cartola.odds.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Politica de seguranca da API: stateless, autenticada por JWT.
 *
 * <p>Matriz de acesso por rota:
 *
 * <pre>
 * publico       POST /api/auth/login, /error
 *               /actuator/health, /actuator/health/**, /actuator/info
 *               /swagger-ui**, /v3/api-docs** (404 no perfil prod — springdoc desligado)
 * ADMIN         /actuator/** (metrics, prometheus)
 *               PATCH /api/config, POST /api/config/reset
 *               DELETE /api/cache, DELETE /api/cache/**
 *               POST /api/historico/{rodada}/atualizar-pontuacao
 *               /api/usuarios/** fora de /me
 * autenticado   /api/usuarios/me, /api/usuarios/me/**
 *               qualquer outra rota
 * </pre>
 *
 * <p>A separacao ADMIN/autenticado nao e cosmetica, e o criterio e um so: <em>escreve na
 * instancia inteira ou gasta cota externa</em>. {@code PATCH /api/config} e
 * {@code POST /api/config/reset} mudam pesos do score, formacao e {@code odd_limite} de
 * toda a instancia; {@code DELETE /api/cache} forca chamadas novas a The Odds API, cuja
 * cota mensal e paga; e {@code POST /api/historico/{rodada}/atualizar-pontuacao} regrava a
 * {@code pontuacaoReal} de todos os atletas da rodada — a tabela de escalacao e da
 * instancia, nao de quem chamou — depois de consultar a API do Cartola. Sao operacoes de
 * dono, nao de convidado.
 *
 * <p><strong>A ordem dos matchers importa:</strong> o primeiro que casa decide. Por isso
 * {@code /api/usuarios/me} vem antes de {@code /api/usuarios/**}, e as regras de ADMIN por
 * metodo vem antes do {@code anyRequest().authenticated()}. Um matcher por metodo cobre
 * <em>so</em> aquele metodo — {@code HEAD} nao herda a autorizacao de {@code GET} —, e por
 * isso as regras de ADMIN aqui restringem apenas os verbos que escrevem: leitura e HEAD
 * caem na regra final e continuam exigindo apenas autenticacao.
 *
 * <p>{@link EnableMethodSecurity} habilita o {@code @PreAuthorize} usado pelo
 * {@code UsuarioController}. Os matchers de {@code /api/usuarios} aqui sao o piso — um
 * endpoint novo criado sem {@code @PreAuthorize} continua fechado para quem nao e ADMIN;
 * a regra fina de cada operacao segue declarada ao lado do endpoint.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";

    private static final String[] ROTAS_PUBLICAS = {
        "/error",
        // Healthcheck da plataforma: precisa responder antes de qualquer token existir.
        // O corpo nao vaza estado de banco e dependencias porque
        // management.endpoint.health.show-details=when_authorized esconde os componentes
        // de quem nao esta autenticado.
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        // Publicos so fora de producao: no perfil prod o springdoc esta desligado
        // (application-prod.properties) e estas rotas simplesmente nao existem — 404.
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UsuarioDetailsService usuarioDetailsService,
            ErroSegurancaHandler erroSegurancaHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        var jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, usuarioDetailsService);

        return http
                // API stateless com token no header: nao ha cookie de sessao para um
                // site de terceiro forjar, entao CSRF nao se aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sem isto, o preflight OPTIONS do navegador cai em anyRequest().authenticated()
                // e volta 401 — o browser nem chega a enviar a requisicao real. Preflight nao
                // carrega Authorization, entao ele precisa passar antes da autorizacao.
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(SecurityConfig::cabecalhos)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(erroSegurancaHandler)
                        .accessDeniedHandler(erroSegurancaHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        // Metricas e prometheus descrevem o interior da aplicacao: uso de
                        // memoria, latencia por rota, contagem de erros. Nada disso e de
                        // convidado — e health e info ja passaram no permitAll acima.
                        .requestMatchers("/actuator/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PATCH, "/api/config").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/config/reset").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/cache", "/api/cache/**").hasRole(ADMIN)
                        // Regrava a pontuacao real de todos os atletas da rodada numa tabela
                        // que e da instancia, nao de quem chamou, e consulta a API do Cartola
                        // antes disso. E a unica escrita global fora de /api/config: as demais
                        // rotas de /api/historico so leem e seguem abertas a qualquer token.
                        .requestMatchers(HttpMethod.POST, "/api/historico/*/atualizar-pontuacao").hasRole(ADMIN)
                        // Antes de /api/usuarios/**: sao as rotas que o usuario tem sobre a
                        // propria conta e valem para qualquer perfil.
                        .requestMatchers("/api/usuarios/me", "/api/usuarios/me/**").authenticated()
                        .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Cabecalhos de defesa da resposta. {@code X-Content-Type-Options: nosniff} e
     * {@code X-Frame-Options: DENY} ja vem ligados por padrao no Spring Security; aqui
     * entram os dois que faltam.
     *
     * <p>O HSTS fica com o matcher padrao do Spring Security, que so o emite quando
     * {@code request.isSecure()}. Atras da borda da plataforma o TLS termina no proxy e o
     * Tomcat veria HTTP puro — quem corrige isso e o {@code RemoteIpValve}, ligado por
     * {@code server.forward-headers-strategy=native} no {@code application.properties}: ele
     * normaliza esquema, host e porta a partir dos {@code X-Forwarded-*} antes de a
     * requisicao chegar aqui, <strong>mas so quando ela vem de um proxy confiavel</strong>
     * ({@code server.tomcat.remoteip.internal-proxies}). De qualquer outra origem os
     * {@code X-Forwarded-*} sao ignorados como o que sao: header que o cliente escreve.
     *
     * <p>A alternativa {@code framework} ({@code ForwardedHeaderFilter}) normaliza igual, mas
     * sem nocao de quem esta do outro lado: qualquer cliente reescreveria esquema e host da
     * requisicao, e um {@code X-Forwarded-Host: evil.example} sairia no {@code Location} de
     * uma resposta {@code 201}. E o mesmo motivo pelo qual o {@code LoginThrottle} recusa o
     * {@code X-Forwarded-For} — nao saber quantos saltos confiar —, so que resolvido em vez
     * de aceito: aqui a lista de saltos confiaveis existe e e configuravel.
     *
     * <p>A condicao evita o outro extremo: mandar HSTS em {@code http://localhost} travaria
     * o navegador do desenvolvedor em HTTPS para todo o host por um ano.
     */
    private static void cabecalhos(HeadersConfigurer<HttpSecurity> headers) {
        headers
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000L));
    }

    /**
     * Montado a partir dos beans {@code UserDetailsService} e {@link PasswordEncoder}
     * pelo proprio Spring Security — e o que o {@code AuthController} usa para validar
     * e-mail e senha no login.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Origens liberadas por ambiente, nunca {@code *}: o token viaja em header e uma origem
     * curinga deixaria qualquer site chamar a API com o token da vitima. Metodos e headers
     * tambem sao listados um a um, pelo mesmo motivo.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String origensPermitidas) {
        var config = new CorsConfiguration();
        // O trim cobre a lista escrita com espaco depois da virgula na variavel de
        // ambiente: " http://app.exemplo" nunca casaria com o Origin enviado pelo browser.
        config.setAllowedOrigins(Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
