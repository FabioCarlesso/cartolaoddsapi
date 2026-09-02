package com.cartola.odds.model.enums;

/**
 * Perfil de acesso do usuario.
 *
 * <p>ADMIN administra a aplicacao (configuracao, cache e usuarios); USER consulta
 * escalacao, ranking, favoritos e historico. A politica por rota vive no
 * {@code SecurityConfig}.
 */
public enum Perfil {

    ADMIN,
    USER;

    /** Nome da authority correspondente no Spring Security (prefixo {@code ROLE_}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
