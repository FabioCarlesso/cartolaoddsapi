package com.cartola.odds.config;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o administrador inicial quando o banco ainda nao tem nenhum ADMIN ativo.
 *
 * <p>E o que permite subir uma instancia nova sem INSERT manual no banco. A senha nunca
 * vem versionada: em producao, sem {@code APP_ADMIN_INICIAL_SENHA} a aplicacao falha ao
 * iniciar; fora dela, apenas avisa e segue sem criar o usuario.
 *
 * <p>Idempotente: nos boots seguintes encontra o admin ativo e nao faz nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInicialBootstrap implements ApplicationRunner {

    /** Piso de tamanho da senha do admin inicial — o primeiro acesso da instancia. */
    private static final int SENHA_MIN_CHARS = 8;

    private final AdminInicialProperties props;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var senha = props.getSenha();
        var senhaAusente = senha == null || senha.isBlank();

        // A validacao vem antes da consulta ao banco de proposito: em producao a variavel
        // e obrigatoria mesmo com admin ja criado, para que desativar o ultimo admin nao
        // deixe a instancia sem caminho de volta.
        if (senhaAusente && ehProducao()) {
            throw new IllegalStateException(
                    "APP_ADMIN_INICIAL_SENHA nao configurada. Em producao a senha do administrador "
                            + "inicial e obrigatoria (minimo de " + SENHA_MIN_CHARS + " caracteres).");
        }
        if (!senhaAusente && senha.length() < SENHA_MIN_CHARS) {
            throw new IllegalStateException(
                    "APP_ADMIN_INICIAL_SENHA tem %d caracteres; o minimo e %d."
                            .formatted(senha.length(), SENHA_MIN_CHARS));
        }

        if (usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)) {
            log.debug("Administrador ativo ja existe: bootstrap do admin inicial ignorado.");
            return;
        }
        if (senhaAusente) {
            log.warn("Nenhum administrador ativo e APP_ADMIN_INICIAL_SENHA nao configurada: "
                    + "o admin inicial nao foi criado e a API fica sem acesso.");
            return;
        }

        var admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail(props.getEmail());
        admin.setSenha(passwordEncoder.encode(senha));
        admin.setPerfil(Perfil.ADMIN);
        admin.setAtivo(true);
        usuarioRepository.save(admin);

        log.info("Administrador inicial criado: {}", props.getEmail());
    }

    private boolean ehProducao() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }
}
