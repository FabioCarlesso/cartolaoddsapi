package com.cartola.odds.repository;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPerfilAndAtivoTrue(Perfil perfil);

    /**
     * Usuarios ativos de um perfil, com lock de escrita nas linhas.
     *
     * <p>E o que sustenta a regra do ultimo administrador. Uma contagem simples seria
     * check-then-act: duas requisicoes simultaneas leriam "ha 2 administradores" e cada
     * uma removeria o seu, deixando a instancia sem nenhum. O {@code SELECT ... FOR UPDATE}
     * serializa as duas, e a segunda ja enxerga o estado que a primeira deixou.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.perfil = :perfil AND u.ativo = true")
    List<Usuario> travarAtivosPorPerfil(@Param("perfil") Perfil perfil);
}
