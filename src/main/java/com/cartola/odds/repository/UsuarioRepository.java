package com.cartola.odds.repository;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByPerfilAndAtivoTrue(Perfil perfil);
}
