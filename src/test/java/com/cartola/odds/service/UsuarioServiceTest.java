package com.cartola.odds.service;

import com.cartola.odds.exception.ConflitoException;
import com.cartola.odds.exception.RecursoNaoEncontradoException;
import com.cartola.odds.exception.SenhaInvalidaException;
import com.cartola.odds.exception.TentativasExcedidasException;
import com.cartola.odds.model.Usuario;
import com.cartola.odds.model.enums.Perfil;
import com.cartola.odds.model.request.AlterarSenhaRequest;
import com.cartola.odds.model.request.UsuarioRequest;
import com.cartola.odds.model.request.UsuarioUpdateRequest;
import com.cartola.odds.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioService")
class UsuarioServiceTest {

    private static final String SENHA = "senha-forte-123";
    private static final String EMAIL_ADMIN = "admin@cartolaodds.local";

    @Mock UsuarioRepository usuarioRepository;
    @Mock LoginThrottle loginThrottle;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UsuarioService service() {
        return new UsuarioService(usuarioRepository, passwordEncoder, loginThrottle);
    }

    private Usuario usuario(Long id, String email, Perfil perfil, boolean ativo) {
        var usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Usuario de teste");
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(SENHA));
        usuario.setPerfil(perfil);
        usuario.setAtivo(ativo);
        return usuario;
    }

    /**
     * Deixa o admin de id 1 como usuario da requisicao, como faria o filtro JWT. As
     * autoprotecoes leem o id direto do principal, sem consultar o banco — por isso a
     * releitura fica em {@link #stubReleituraDoLogado(Usuario)}, so para quem precisa.
     */
    private Usuario autenticarAdmin() {
        var admin = usuario(1L, EMAIL_ADMIN, Perfil.ADMIN, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        return admin;
    }

    private void stubReleituraDoLogado(Usuario logado) {
        when(usuarioRepository.findByEmailIgnoreCase(logado.getEmail())).thenReturn(Optional.of(logado));
    }

    private void stubAdminsAtivos(Usuario... admins) {
        when(usuarioRepository.travarAtivosPorPerfil(Perfil.ADMIN)).thenReturn(List.of(admins));
    }

    private Usuario salvoNoRepositorio() {
        var captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        return captor.getValue();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    // ── Criacao ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deve criar usuario com senha em hash e e-mail normalizado")
    void deveCriarUsuario() {
        when(usuarioRepository.existsByEmailIgnoreCase("novo@cartolaodds.local")).thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UsuarioRequest();
        request.setNome("  Novo Usuario  ");
        request.setEmail("  Novo@CartolaOdds.local ");
        request.setSenha(SENHA);
        request.setPerfil(Perfil.ADMIN);

        var response = service().criar(request);

        assertThat(response.getEmail()).isEqualTo("novo@cartolaodds.local");
        assertThat(response.getNome()).isEqualTo("Novo Usuario");
        assertThat(response.getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(response.isAtivo()).isTrue();

        var salvo = salvoNoRepositorio();
        assertThat(salvo.getSenha()).isNotEqualTo(SENHA).startsWith("$2");
        assertThat(passwordEncoder.matches(SENHA, salvo.getSenha())).isTrue();
    }

    @Test
    @DisplayName("deve criar como USER quando o perfil nao vem no payload")
    void deveCriarComoUserPorPadrao() {
        when(usuarioRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UsuarioRequest();
        request.setNome("Novo Usuario");
        request.setEmail("novo@cartolaodds.local");
        request.setSenha(SENHA);

        assertThat(service().criar(request).getPerfil()).isEqualTo(Perfil.USER);
    }

    @Test
    @DisplayName("deve recusar criacao com e-mail ja cadastrado, ignorando a caixa")
    void deveRecusarEmailDuplicado() {
        when(usuarioRepository.existsByEmailIgnoreCase("existente@cartolaodds.local")).thenReturn(true);

        var request = new UsuarioRequest();
        request.setNome("Novo Usuario");
        request.setEmail("EXISTENTE@cartolaodds.local");
        request.setSenha(SENHA);

        assertThatThrownBy(() -> service().criar(request))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("e-mail informado");
        verify(usuarioRepository, never()).save(any());
    }

    // ── Consulta ──────────────────────────────────────────────────────

    @Test
    @DisplayName("deve devolver a pagina de usuarios no envelope da API")
    void deveListarUsuarios() {
        var pageable = PageRequest.of(0, 20);
        when(usuarioRepository.findAll(pageable)).thenReturn(new PageImpl<>(
                List.of(usuario(1L, EMAIL_ADMIN, Perfil.ADMIN, true),
                        usuario(2L, "user@cartolaodds.local", Perfil.USER, false)),
                pageable, 2));

        var pagina = service().listar(pageable);

        assertThat(pagina.getConteudo()).hasSize(2);
        assertThat(pagina.getPagina()).isZero();
        assertThat(pagina.getTamanho()).isEqualTo(20);
        assertThat(pagina.getTotalElementos()).isEqualTo(2);
        assertThat(pagina.getTotalPaginas()).isEqualTo(1);
        assertThat(pagina.isUltima()).isTrue();
    }

    @Test
    @DisplayName("deve lancar 404 ao buscar id inexistente")
    void deveLancarNaoEncontrado() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().buscarPorId(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("deve devolver o dono do token em buscarLogado")
    void deveBuscarLogado() {
        stubReleituraDoLogado(autenticarAdmin());

        var response = service().buscarLogado();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo(EMAIL_ADMIN);
    }

    // ── Atualizacao ───────────────────────────────────────────────────

    @Test
    @DisplayName("deve atualizar nome e e-mail sem tocar na senha nem na tokenVersion")
    void deveAtualizarNomeEEmail() {
        var alvo = usuario(2L, "user@cartolaodds.local", Perfil.USER, true);
        var hashOriginal = alvo.getSenha();
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(alvo));
        when(usuarioRepository.existsByEmailIgnoreCase("outro@cartolaodds.local")).thenReturn(false);
        when(usuarioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UsuarioUpdateRequest();
        request.setNome("Nome Novo");
        request.setEmail("Outro@CartolaOdds.local");

        var response = service().atualizar(2L, request);

        assertThat(response.getNome()).isEqualTo("Nome Novo");
        assertThat(response.getEmail()).isEqualTo("outro@cartolaodds.local");
        assertThat(alvo.getSenha()).isEqualTo(hashOriginal);
        assertThat(alvo.getTokenVersion()).isZero();
    }

    @Test
    @DisplayName("deve recusar atualizacao para e-mail de outro usuario")
    void deveRecusarEmailDeOutroUsuario() {
        var alvo = usuario(2L, "user@cartolaodds.local", Perfil.USER, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(alvo));
        when(usuarioRepository.existsByEmailIgnoreCase(EMAIL_ADMIN)).thenReturn(true);

        var request = new UsuarioUpdateRequest();
        request.setEmail(EMAIL_ADMIN);

        assertThatThrownBy(() -> service().atualizar(2L, request))
                .isInstanceOf(ConflitoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve aceitar o proprio e-mail do usuario na atualizacao")
    void deveAceitarProprioEmail() {
        var alvo = usuario(2L, "user@cartolaodds.local", Perfil.USER, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(alvo));
        when(usuarioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UsuarioUpdateRequest();
        request.setEmail("USER@cartolaodds.local");

        assertThat(service().atualizar(2L, request).getEmail()).isEqualTo("user@cartolaodds.local");
    }

    @Test
    @DisplayName("deve incrementar tokenVersion ao rebaixar o perfil de um administrador")
    void deveIncrementarTokenVersionAoRebaixar() {
        var admin = autenticarAdmin();
        var outroAdmin = usuario(2L, "outro@cartolaodds.local", Perfil.ADMIN, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(outroAdmin));
        stubAdminsAtivos(admin, outroAdmin);
        when(usuarioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UsuarioUpdateRequest();
        request.setPerfil(Perfil.USER);

        assertThat(service().atualizar(2L, request).getPerfil()).isEqualTo(Perfil.USER);
        assertThat(outroAdmin.getTokenVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("nao deve mexer na tokenVersion ao promover um usuario a administrador")
    void naoDeveIncrementarTokenVersionAoPromover() {
        var alvo = usuario(2L, "user@cartolaodds.local", Perfil.USER, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(alvo));
        when(usuarioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UsuarioUpdateRequest();
        request.setPerfil(Perfil.ADMIN);

        assertThat(service().atualizar(2L, request).getPerfil()).isEqualTo(Perfil.ADMIN);
        assertThat(alvo.getTokenVersion()).isZero();
    }

    @Test
    @DisplayName("deve recusar o rebaixamento do proprio perfil do administrador logado")
    void deveRecusarAutoRebaixamento() {
        var admin = autenticarAdmin();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        var request = new UsuarioUpdateRequest();
        request.setPerfil(Perfil.USER);

        assertThatThrownBy(() -> service().atualizar(1L, request))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("proprio perfil");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve recusar a autodesativacao do administrador logado")
    void deveRecusarAutoDesativacao() {
        var admin = autenticarAdmin();
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service().desativar(1L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("propria conta");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve recusar o rebaixamento do ultimo administrador ativo")
    void deveRecusarRebaixarUltimoAdmin() {
        autenticarAdmin();
        var ultimoAdmin = usuario(2L, "outro@cartolaodds.local", Perfil.ADMIN, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(ultimoAdmin));
        stubAdminsAtivos(ultimoAdmin);

        var request = new UsuarioUpdateRequest();
        request.setPerfil(Perfil.USER);

        assertThatThrownBy(() -> service().atualizar(2L, request))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("ultimo administrador");
        assertThat(ultimoAdmin.getPerfil()).isEqualTo(Perfil.ADMIN);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve recusar a desativacao do ultimo administrador ativo")
    void deveRecusarDesativarUltimoAdmin() {
        autenticarAdmin();
        var ultimoAdmin = usuario(2L, "outro@cartolaodds.local", Perfil.ADMIN, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(ultimoAdmin));
        stubAdminsAtivos(ultimoAdmin);

        assertThatThrownBy(() -> service().desativar(2L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("ultimo administrador");
        assertThat(ultimoAdmin.isAtivo()).isTrue();
        verify(usuarioRepository, never()).save(any());
    }

    // ── Desativacao ───────────────────────────────────────────────────

    @Test
    @DisplayName("deve desativar logicamente e invalidar os tokens ja emitidos")
    void deveDesativarLogicamente() {
        autenticarAdmin();
        var alvo = usuario(2L, "user@cartolaodds.local", Perfil.USER, true);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(alvo));

        service().desativar(2L);

        var salvo = salvoNoRepositorio();
        assertThat(salvo.isAtivo()).isFalse();
        assertThat(salvo.getTokenVersion()).isEqualTo(1L);
        assertThat(salvo.getEmail()).isEqualTo("user@cartolaodds.local");
    }

    @Test
    @DisplayName("deve ser idempotente ao desativar quem ja esta inativo")
    void deveIgnorarDesativacaoRepetida() {
        var alvo = usuario(2L, "user@cartolaodds.local", Perfil.USER, false);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(alvo));

        service().desativar(2L);

        assertThat(alvo.getTokenVersion()).isZero();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve reativar usuario sem mexer na tokenVersion")
    void deveReativarUsuario() {
        var alvo = usuario(2L, "user@cartolaodds.local", Perfil.USER, false);
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(alvo));
        when(usuarioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new UsuarioUpdateRequest();
        request.setAtivo(true);

        assertThat(service().atualizar(2L, request).isAtivo()).isTrue();
        assertThat(alvo.getTokenVersion()).isZero();
    }

    // ── Ordenacao da listagem ─────────────────────────────────────────

    @Test
    @DisplayName("deve recusar ordenacao por campo que a entidade nao tem")
    void deveRecusarOrdenacaoDesconhecida() {
        var pageable = PageRequest.of(0, 20, Sort.by("naoExiste"));

        assertThatThrownBy(() -> service().listar(pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Campos aceitos")
                // O campo recebido do cliente nao volta ecoado na mensagem.
                .hasMessageNotContaining("naoExiste");
        verify(usuarioRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("deve recusar ordenacao pela senha, ainda que a propriedade exista")
    void deveRecusarOrdenacaoPorSenha() {
        var pageable = PageRequest.of(0, 20, Sort.by("senha"));

        assertThatThrownBy(() -> service().listar(pageable))
                .isInstanceOf(IllegalArgumentException.class);
        verify(usuarioRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("deve aceitar os campos suportados de ordenacao")
    void deveAceitarOrdenacaoSuportada() {
        var pageable = PageRequest.of(0, 20, Sort.by("criadoEm").descending().and(Sort.by("email")));
        when(usuarioRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(service().listar(pageable).getConteudo()).isEmpty();
    }

    // ── Freio de forca bruta na troca de senha ────────────────────────

    @Test
    @DisplayName("deve registrar falha no freio quando a senha atual esta errada")
    void deveRegistrarFalhaNoFreio() {
        stubReleituraDoLogado(autenticarAdmin());

        var request = new AlterarSenhaRequest();
        request.setSenhaAtual("senha-errada");
        request.setNovaSenha("senha-nova-456");

        assertThatThrownBy(() -> service().alterarSenha(request))
                .isInstanceOf(SenhaInvalidaException.class);

        verify(loginThrottle).verificar(EMAIL_ADMIN);
        verify(loginThrottle).registrarFalha(EMAIL_ADMIN);
        verify(loginThrottle, never()).registrarSucesso(EMAIL_ADMIN);
    }

    @Test
    @DisplayName("deve zerar o freio quando a senha atual confere")
    void deveZerarFreioNaTrocaBemSucedida() {
        stubReleituraDoLogado(autenticarAdmin());

        var request = new AlterarSenhaRequest();
        request.setSenhaAtual(SENHA);
        request.setNovaSenha("senha-nova-456");

        service().alterarSenha(request);

        verify(loginThrottle).registrarSucesso(EMAIL_ADMIN);
        verify(loginThrottle, never()).registrarFalha(EMAIL_ADMIN);
    }

    @Test
    @DisplayName("deve barrar a troca antes de conferir a senha quando o freio ja bloqueou o e-mail")
    void deveBarrarTrocaComFreioAtivo() {
        stubReleituraDoLogado(autenticarAdmin());
        doThrow(new TentativasExcedidasException("Muitas tentativas malsucedidas."))
                .when(loginThrottle).verificar(EMAIL_ADMIN);

        var request = new AlterarSenhaRequest();
        request.setSenhaAtual(SENHA);
        request.setNovaSenha("senha-nova-456");

        assertThatThrownBy(() -> service().alterarSenha(request))
                .isInstanceOf(TentativasExcedidasException.class);
        verify(usuarioRepository, never()).save(any());
    }

    // ── Troca de senha ────────────────────────────────────────────────

    @Test
    @DisplayName("deve trocar a senha e invalidar os tokens anteriores")
    void deveTrocarSenha() {
        var admin = autenticarAdmin();
        stubReleituraDoLogado(admin);

        var request = new AlterarSenhaRequest();
        request.setSenhaAtual(SENHA);
        request.setNovaSenha("senha-nova-456");

        service().alterarSenha(request);

        var salvo = salvoNoRepositorio();
        assertThat(passwordEncoder.matches("senha-nova-456", salvo.getSenha())).isTrue();
        assertThat(salvo.getTokenVersion()).isEqualTo(1L);
        assertThat(salvo).isSameAs(admin);
    }

    @Test
    @DisplayName("deve recusar a troca quando a senha atual nao confere")
    void deveRecusarSenhaAtualErrada() {
        stubReleituraDoLogado(autenticarAdmin());

        var request = new AlterarSenhaRequest();
        request.setSenhaAtual("senha-errada");
        request.setNovaSenha("senha-nova-456");

        assertThatThrownBy(() -> service().alterarSenha(request))
                .isInstanceOf(SenhaInvalidaException.class);
        verify(usuarioRepository, never()).save(any());
    }
}
