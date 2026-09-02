package com.cartola.odds.service;

import com.cartola.odds.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    /**
     * A mensagem e generica de proposito: o {@code DaoAuthenticationProvider} converte
     * esta excecao em credencial invalida, e o cliente nao deve distinguir e-mail
     * inexistente de senha errada.
     */
    @Override
    @Transactional(readOnly = true)
    public com.cartola.odds.model.Usuario loadUserByUsername(String email) {
        return usuarioRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais invalidas."));
    }
}
