package com.cartola.odds.service;

import com.cartola.odds.exception.ConflitoException;
import com.cartola.odds.exception.RecursoNaoEncontradoException;
import com.cartola.odds.exception.SenhaInvalidaException;
import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.model.request.AlterarSenhaRequest;
import com.cartola.odds.model.request.UsuarioRequest;
import com.cartola.odds.model.request.UsuarioUpdateRequest;
import com.cartola.odds.model.response.PaginaResponse;
import com.cartola.odds.model.response.UsuarioResponse;
import com.cartola.odds.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Cadastro e manutencao de usuarios.
 *
 * <p>Quem pode chamar cada operacao esta declarado no {@code UsuarioController}, com
 * {@code @PreAuthorize} ao lado do endpoint — fonte unica de verdade, para nao repetir a
 * regra tambem em matcher de URL no {@code SecurityConfig}.
 *
 * <p>Tres momentos incrementam a {@code tokenVersion} e derrubam os tokens ja emitidos:
 * troca de senha, desativacao e rebaixamento de perfil. Sem isso, um usuario recem-
 * -rebaixado continuaria administrando a aplicacao ate o token expirar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    /**
     * Campos aceitos no {@code sort} da listagem. A lista existe por dois motivos: uma
     * propriedade inexistente derrubava a requisicao em 500 vindo do Spring Data, e
     * {@code sort=senha} era aceito — ordenar pelo hash nao o revela, mas nada na API
     * deveria alcanca-lo.
     */
    private static final List<String> CAMPOS_ORDENAVEIS =
            List.of("id", "nome", "email", "perfil", "ativo", "criadoEm");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginThrottle loginThrottle;

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        var email = normalizar(request.getEmail());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Ja existe um usuario com o e-mail informado.");
        }

        var usuario = new Usuario();
        usuario.setNome(request.getNome().trim());
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(request.getSenha()));
        usuario.setPerfil(request.getPerfil() != null ? request.getPerfil() : Perfil.USER);
        usuario.setAtivo(true);

        var salvo = usuarioRepository.save(usuario);
        log.info("Usuario criado: {} ({})", salvo.getEmail(), salvo.getPerfil());
        return UsuarioResponse.from(salvo);
    }

    public PaginaResponse<UsuarioResponse> listar(Pageable pageable) {
        validarOrdenacao(pageable.getSort());
        return PaginaResponse.from(usuarioRepository.findAll(pageable).map(UsuarioResponse::from));
    }

    public UsuarioResponse buscarPorId(Long id) {
        return UsuarioResponse.from(buscarEntidade(id));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioUpdateRequest request) {
        var usuario = buscarEntidade(id);

        if (request.getNome() != null) {
            usuario.setNome(request.getNome().trim());
        }
        if (request.getEmail() != null) {
            aplicarEmail(usuario, normalizar(request.getEmail()));
        }
        if (request.getPerfil() != null) {
            aplicarPerfil(usuario, request.getPerfil());
        }
        if (request.getAtivo() != null) {
            aplicarAtivo(usuario, request.getAtivo());
        }

        var salvo = usuarioRepository.save(usuario);
        log.info("Usuario atualizado: id={} perfil={} ativo={}",
                salvo.getId(), salvo.getPerfil(), salvo.isAtivo());
        return UsuarioResponse.from(salvo);
    }

    /**
     * Desativacao logica: o registro continua no banco para preservar o historico, e o
     * usuario apenas deixa de autenticar.
     */
    @Transactional
    public void desativar(Long id) {
        var usuario = buscarEntidade(id);
        if (!usuario.isAtivo()) {
            return;
        }

        aplicarAtivo(usuario, false);
        usuarioRepository.save(usuario);
        log.info("Usuario desativado: id={} ({})", usuario.getId(), usuario.getEmail());
    }

    public UsuarioResponse buscarLogado() {
        return UsuarioResponse.from(usuarioAutenticado());
    }

    /**
     * O mesmo freio de forca bruta do login protege a conferencia da senha atual aqui.
     * Sem ele, um token roubado — cuja validade e limitada — daria tentativas ilimitadas
     * para adivinhar a senha e, acertando, tomar a conta em definitivo: a troca derruba
     * os tokens do dono legitimo.
     */
    @Transactional
    public void alterarSenha(AlterarSenhaRequest request) {
        var usuario = usuarioAutenticado();
        loginThrottle.verificar(usuario.getEmail());

        if (!passwordEncoder.matches(request.getSenhaAtual(), usuario.getSenha())) {
            loginThrottle.registrarFalha(usuario.getEmail());
            log.warn("Troca de senha recusada para {}: senha atual incorreta.", usuario.getEmail());
            throw new SenhaInvalidaException("A senha atual informada nao confere.");
        }

        loginThrottle.registrarSucesso(usuario.getEmail());
        usuario.setSenha(passwordEncoder.encode(request.getNovaSenha()));
        usuario.incrementarTokenVersion();
        usuarioRepository.save(usuario);
        log.info("Senha alterada para {}: tokens anteriores invalidados.", usuario.getEmail());
    }

    // ── Regras de alteracao ───────────────────────────────────────────

    /**
     * O e-mail e o {@code subject} do token: trocado o e-mail, os tokens antigos deixam
     * de resolver um usuario e caem sozinhos — nao ha {@code tokenVersion} a incrementar.
     */
    private void aplicarEmail(Usuario usuario, String email) {
        if (email.equalsIgnoreCase(usuario.getEmail())) {
            return;
        }
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Ja existe um usuario com o e-mail informado.");
        }
        usuario.setEmail(email);
    }

    private void aplicarPerfil(Usuario usuario, Perfil perfil) {
        if (perfil == usuario.getPerfil()) {
            return;
        }

        var rebaixando = usuario.getPerfil() == Perfil.ADMIN;
        if (rebaixando) {
            verificarProprioAdmin(usuario, "rebaixar o proprio perfil");
            verificarUltimoAdmin(usuario, "rebaixar o perfil do ultimo administrador ativo");
            usuario.incrementarTokenVersion();
        }
        usuario.setPerfil(perfil);
    }

    private void aplicarAtivo(Usuario usuario, boolean ativo) {
        if (ativo == usuario.isAtivo()) {
            return;
        }

        if (!ativo) {
            verificarProprioAdmin(usuario, "desativar a propria conta");
            verificarUltimoAdmin(usuario, "desativar o ultimo administrador ativo");
            // Sem isto o token ja emitido continuaria valendo ate expirar, e o usuario
            // desativado seguiria usando a API.
            usuario.incrementarTokenVersion();
        }
        usuario.setAtivo(ativo);
    }

    /**
     * Um administrador que se desativa ou se rebaixa perde o acesso no ato — quase sempre
     * por engano, e sem ninguem para desfazer se ele for o unico.
     */
    private void verificarProprioAdmin(Usuario alvo, String acao) {
        if (idDoUsuarioAutenticado().equals(alvo.getId())) {
            throw new ConflitoException("Um administrador nao pode " + acao + ".");
        }
    }

    /**
     * Uma instancia sem administrador ativo so voltaria a ter um por acesso ao banco.
     *
     * <p>A consulta trava as linhas dos administradores ativos ({@code SELECT ... FOR
     * UPDATE}) em vez de apenas conta-las: duas requisicoes simultaneas contando veriam
     * "ha 2" e cada uma removeria o seu, justamente o resultado que esta regra existe
     * para impedir.
     */
    private void verificarUltimoAdmin(Usuario alvo, String acao) {
        if (alvo.getPerfil() == Perfil.ADMIN
                && alvo.isAtivo()
                && usuarioRepository.travarAtivosPorPerfil(Perfil.ADMIN).size() <= 1) {
            throw new ConflitoException("Nao e possivel " + acao + ".");
        }
    }

    /** Ordenacao restrita: campo desconhecido vira 400, e nao o 500 do Spring Data. */
    private void validarOrdenacao(Sort sort) {
        var invalido = sort.stream()
                .map(Sort.Order::getProperty)
                .anyMatch(campo -> !CAMPOS_ORDENAVEIS.contains(campo));

        if (invalido) {
            // O campo recebido nao e ecoado: a lista de aceitos ja basta para corrigir a
            // chamada, e evita devolver ao cliente um texto que ele mesmo escolheu.
            throw new IllegalArgumentException(
                    "Ordenacao nao suportada. Campos aceitos: " + String.join(", ", CAMPOS_ORDENAVEIS) + ".");
        }
    }

    // ── Apoio ─────────────────────────────────────────────────────────

    private Usuario buscarEntidade(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuario nao encontrado para o id " + id + "."));
    }

    /**
     * Id do usuario da requisicao, lido direto do principal: id nao muda depois da
     * emissao do token, entao nao ha o que recarregar — e as autoprotecoes so comparam
     * identidade. Evita uma consulta por checagem.
     */
    private Long idDoUsuarioAutenticado() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Usuario usuario) {
            return usuario.getId();
        }
        return usuarioAutenticado().getId();
    }

    /**
     * Recarrega o usuario do banco, em vez de usar o principal do token: o principal e a
     * foto do usuario no momento da autenticacao, e {@code /me} e a troca de senha
     * precisam do estado e do hash atuais.
     */
    private Usuario usuarioAutenticado() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || autenticacao.getName() == null) {
            throw new IllegalStateException("Nenhum usuario autenticado no contexto.");
        }
        return usuarioRepository.findByEmailIgnoreCase(autenticacao.getName())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Usuario autenticado nao encontrado."));
    }

    /** E-mail sempre em minusculas: a unicidade no banco e sensivel a caixa, o login nao. */
    private String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
