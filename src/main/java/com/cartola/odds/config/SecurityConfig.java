package com.cartola.odds.config;

import com.cartola.odds.security.ErroSegurancaHandler;
import com.cartola.odds.security.JwtAuthenticationFilter;
import com.cartola.odds.service.JwtService;
import com.cartola.odds.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Politica de seguranca da API: stateless, autenticada por JWT.
 *
 * <p>A regra aqui e grossa de proposito — publico para login, documentacao e Actuator;
 * autenticado para todo o resto. A matriz fina por rota (quem precisa ser ADMIN para
 * alterar configuracao ou invalidar cache) e o fechamento da documentacao em producao
 * vem na issue #38.
 *
 * <p>{@link EnableMethodSecurity} habilita o {@code @PreAuthorize} usado pelo
 * {@code UsuarioController}: as rotas de {@code /api/usuarios} misturam operacoes de
 * administrador com as do proprio usuario, e declarar isso ao lado do endpoint evita uma
 * segunda fonte de verdade em matcher de URL aqui.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] ROTAS_PUBLICAS = {
        "/api/auth/**",
        "/error",
        // Actuator hoje roda em porta propria (management.server.port), fora do alcance
        // da internet; a restricao por perfil chega na issue #38, junto com o deploy.
        "/actuator/**",
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
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(erroSegurancaHandler)
                        .accessDeniedHandler(erroSegurancaHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ROTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
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
     * curinga deixaria qualquer site chamar a API com o token da vitima.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String origensPermitidas) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origensPermitidas.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
