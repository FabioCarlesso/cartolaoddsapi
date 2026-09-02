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
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Politica de seguranca da API: stateless, autenticada por JWT.
 *
 * <p>A regra aqui e grossa de proposito — publico para login, documentacao e Actuator;
 * autenticado para todo o resto. A matriz fina por rota (quem precisa ser ADMIN para
 * alterar configuracao ou invalidar cache) e o fechamento da documentacao em producao
 * vem na issue #38.
 */
@Configuration
@EnableWebSecurity
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
            ErroSegurancaHandler erroSegurancaHandler) throws Exception {

        var jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, usuarioDetailsService);

        return http
                // API stateless com token no header: nao ha cookie de sessao para um
                // site de terceiro forjar, entao CSRF nao se aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
}
