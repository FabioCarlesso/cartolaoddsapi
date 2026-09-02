package com.cartola.odds.model;

import com.cartola.odds.model.enums.Perfil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 180, unique = true)
    private String email;

    /** Hash BCrypt da senha — nunca o valor em claro. */
    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Perfil perfil = Perfil.USER;

    @Column(nullable = false)
    private boolean ativo = true;

    /**
     * Contador que invalida tokens ja emitidos sem manter sessao no servidor: o valor
     * viaja como claim no JWT e o filtro recusa o token quando ele diverge do banco.
     * Trocar a senha ou desativar o usuario incrementa o contador.
     */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0L;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public void incrementarTokenVersion() {
        this.tokenVersion++;
    }

    // ── Contrato UserDetails ──────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(perfil.authority()));
    }

    /** O Spring Security identifica o usuario pelo e-mail. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }
}
