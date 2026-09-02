package com.cartola.odds.config;

import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria o administrador inicial quando o banco ainda nao tem nenhum ADMIN ativo.
 *
 * <p>E o que permite subir uma instancia nova sem INSERT manual no banco. A senha nunca
 * vem versionada: sem {@code APP_ADMIN_INICIAL_SENHA} e sem nenhum administrador ativo,
 * a aplicacao <strong>falha ao iniciar</strong> — em qualquer perfil. Subir uma API que
 * ninguem consegue autenticar e pior do que nao subir, e o aviso em log passava batido.
 *
 * <p>Com um administrador ativo no banco, a variavel deixa de ser necessaria: ela e
 * exigida so quando ha um usuario a criar, para nao obrigar producao a carregar para
 * sempre a senha do primeiro acesso depois que ela ja foi trocada.
 *
 * <p>Roda como {@link SmartInitializingSingleton}, e nao como {@code ApplicationRunner}
 * nem no {@code ContextRefreshedEvent}: os dois acontecem depois de o Tomcat aceitar
 * conexao — verificado no log, que trazia "Tomcat started on port 8080" antes da falha.
 * Aqui a checagem acontece ao fim da instanciacao dos singletons, ainda dentro do
 * refresh do contexto, entao uma configuracao faltando derruba o boot antes de a porta
 * abrir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInicialBootstrap implements SmartInitializingSingleton {

    /** Piso de tamanho da senha do admin inicial — o primeiro acesso da instancia. */
    private static final int SENHA_MIN_CHARS = 8;

    private final AdminInicialProperties props;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * O {@code @Transactional} fica aqui, e nao no {@link #executar()}: quem chama este
     * metodo e o container, atraves do proxy do bean, entao a transacao e de fato
     * aplicada. Chamar {@code executar()} de dentro da classe seria auto-invocacao, e o
     * proxy nao entraria no caminho.
     */
    @Override
    @Transactional
    public void afterSingletonsInstantiated() {
        executar();
    }

    /** Idempotente: com um administrador ativo no banco, nao faz nada. */
    public void executar() {
        if (usuarioRepository.existsByPerfilAndAtivoTrue(Perfil.ADMIN)) {
            log.debug("Administrador ativo ja existe: bootstrap do admin inicial ignorado.");
            return;
        }

        var senha = props.getSenha();
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException(
                    "Nenhum administrador ativo no banco e APP_ADMIN_INICIAL_SENHA nao configurada. "
                            + "Defina a variavel (minimo de " + SENHA_MIN_CHARS + " caracteres) para que o "
                            + "administrador inicial seja criado — sem ela a API sobe sem nenhum acesso.");
        }
        if (senha.length() < SENHA_MIN_CHARS) {
            throw new IllegalStateException(
                    "APP_ADMIN_INICIAL_SENHA tem %d caracteres; o minimo e %d."
                            .formatted(senha.length(), SENHA_MIN_CHARS));
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
}
