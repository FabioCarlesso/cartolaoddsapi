package com.cartola.odds.repository;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPerfilAndAtivoTrue(Perfil perfil);

    /** Usado para recusar a operacao que deixaria a instancia sem nenhum administrador. */
    long countByPerfilAndAtivoTrue(Perfil perfil);
}
