package com.cartola.odds.service;

import com.cartola.odds.client.CartolaClient;
import com.cartola.odds.exception.RecursoNaoEncontradoException;
import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.EscalacaoRodada;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.model.response.MercadoStatusResponse;
import com.cartola.odds.model.response.PontuadosResponse;
import com.cartola.odds.repository.EscalacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EscalacaoService")
class EscalacaoServiceTest {

    @Mock EscalacaoRepository repository;
    @Mock CartolaClient cartolaClient;

    @InjectMocks EscalacaoService service;

    // ── salvarEscalacao ─────────────────────────────────────────────

    @Test
    @DisplayName("deve persistir todos os atletas de uma rodada nova com flags corretas")
    @SuppressWarnings("unchecked")
    void devePersistirRodadaNova() {
        when(repository.existsByRodadaId(15)).thenReturn(false);

        service.salvarEscalacao(criarTime(15), 15);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        List<EscalacaoRodada> registros = captor.getValue();

        // 1 GOL titular + 1 ATA titular + 1 ATA reserva = 3
        assertThat(registros).hasSize(3);

        var capitao = registros.stream().filter(EscalacaoRodada::isCapitao).findFirst().orElseThrow();
        assertThat(capitao.getApelido()).isEqualTo("Hulk");
        assertThat(capitao.getPosicao()).isEqualTo("ATA");
        assertThat(capitao.getRodadaId()).isEqualTo(15);
        assertThat(capitao.getScoreSugerido()).isEqualTo(8.54);

        var reservaLuxo = registros.stream().filter(EscalacaoRodada::isReservaLuxo).findFirst().orElseThrow();
        assertThat(reservaLuxo.getApelido()).isEqualTo("Cano");

        assertThat(registros).allSatisfy(r -> assertThat(r.getPontuacaoReal()).isNull());
    }

    @Test
    @DisplayName("nao deve sobrescrever escalacao de rodada ja existente (idempotente)")
    void naoDeveSobrescreverRodadaExistente() {
        when(repository.existsByRodadaId(15)).thenReturn(true);

        service.salvarEscalacao(criarTime(15), 15);

        verify(repository, never()).saveAll(any());
    }

    // ── atualizarPontuacaoReal ──────────────────────────────────────

    @Test
    @DisplayName("deve preencher pontuacaoReal dos atletas encontrados em /pontuados")
    void devePreencherPontuacaoReal() {
        var hulk = registro(15, 1, "Hulk", true);
        var cano = registro(15, 2, "Cano", false);
        when(repository.findByRodadaIdOrderById(15)).thenReturn(List.of(hulk, cano));
        when(cartolaClient.buscarStatusMercado()).thenReturn(status(15));
        when(cartolaClient.buscarPontuados(15)).thenReturn(pontuados(Map.of(1, 8.5, 2, 5.0)));

        var response = service.atualizarPontuacaoReal(15);

        assertThat(hulk.getPontuacaoReal()).isEqualTo(8.5);
        assertThat(cano.getPontuacaoReal()).isEqualTo(5.0);
        assertThat(response.getRodadaId()).isEqualTo(15);
        verify(repository).saveAll(List.of(hulk, cano));
    }

    @Test
    @DisplayName("deve manter pontuacaoReal null para atleta ausente em /pontuados")
    void deveManterNullQuandoAtletaAusente() {
        var hulk = registro(15, 1, "Hulk", true);
        var cano = registro(15, 2, "Cano", false);
        when(repository.findByRodadaIdOrderById(15)).thenReturn(List.of(hulk, cano));
        when(cartolaClient.buscarStatusMercado()).thenReturn(status(15));
        when(cartolaClient.buscarPontuados(15)).thenReturn(pontuados(Map.of(1, 8.5)));

        service.atualizarPontuacaoReal(15);

        assertThat(hulk.getPontuacaoReal()).isEqualTo(8.5);
        assertThat(cano.getPontuacaoReal()).isNull();
    }

    @Test
    @DisplayName("deve lancar 404 ao atualizar rodada sem escalacao registrada")
    void deveLancar404AoAtualizarRodadaInexistente() {
        when(repository.findByRodadaIdOrderById(99)).thenReturn(List.of());

        assertThatThrownBy(() -> service.atualizarPontuacaoReal(99))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("deve rejeitar atualizacao de rodada diferente da corrente")
    void deveRejeitarRodadaNaoCorrente() {
        when(repository.findByRodadaIdOrderById(14)).thenReturn(List.of(registro(14, 1, "Hulk", true)));
        when(cartolaClient.buscarStatusMercado()).thenReturn(status(16));

        assertThatThrownBy(() -> service.atualizarPontuacaoReal(14))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rodada corrente");

        verify(repository, never()).saveAll(any());
    }

    // ── buscarHistorico ─────────────────────────────────────────────

    @Test
    @DisplayName("deve retornar lista vazia quando nao ha rodadas registradas")
    void deveRetornarHistoricoVazio() {
        when(repository.findAllByOrderByRodadaIdAscIdAsc()).thenReturn(List.of());

        var response = service.buscarHistorico();

        assertThat(response.getTotalRodadas()).isZero();
        assertThat(response.getRodadas()).isEmpty();
    }

    @Test
    @DisplayName("deve somar score sugerido e pontuacao real (capitao dobrado) por rodada")
    void deveResumirRodadaComPontuacao() {
        var capitao = registro(15, 1, "Hulk", true);
        capitao.setScoreSugerido(10.0);
        capitao.setPontuacaoReal(8.0);
        var outro = registro(15, 2, "Cano", false);
        outro.setScoreSugerido(5.0);
        outro.setPontuacaoReal(3.0);
        when(repository.findAllByOrderByRodadaIdAscIdAsc()).thenReturn(List.of(capitao, outro));

        var response = service.buscarHistorico();

        assertThat(response.getTotalRodadas()).isEqualTo(1);
        var resumo = response.getRodadas().get(0);
        assertThat(resumo.getRodadaId()).isEqualTo(15);
        assertThat(resumo.getTotalAtletas()).isEqualTo(2);
        assertThat(resumo.getScoreSugeridoTotal()).isEqualTo(15.0);
        assertThat(resumo.isPontuacaoRealDisponivel()).isTrue();
        // 8.0 * 2 (capitao) + 3.0 = 19.0
        assertThat(resumo.getPontuacaoRealTotal()).isEqualTo(19.0);
    }

    @Test
    @DisplayName("deve marcar pontuacaoRealTotal null quando rodada nao foi atualizada")
    void deveResumirRodadaSemPontuacao() {
        var atleta = registro(16, 1, "Hulk", true);
        atleta.setScoreSugerido(7.0);
        when(repository.findAllByOrderByRodadaIdAscIdAsc()).thenReturn(List.of(atleta));

        var response = service.buscarHistorico();

        var resumo = response.getRodadas().get(0);
        assertThat(resumo.isPontuacaoRealDisponivel()).isFalse();
        assertThat(resumo.getPontuacaoRealTotal()).isNull();
    }

    // ── buscarPorRodada ─────────────────────────────────────────────

    @Test
    @DisplayName("deve retornar atletas com detalhes de uma rodada existente")
    void deveRetornarDetalheRodada() {
        when(repository.findByRodadaIdOrderById(15)).thenReturn(List.of(registro(15, 1, "Hulk", true)));

        var response = service.buscarPorRodada(15);

        assertThat(response.getRodadaId()).isEqualTo(15);
        assertThat(response.getAtletas()).hasSize(1);
        assertThat(response.getAtletas().get(0).getApelido()).isEqualTo("Hulk");
        assertThat(response.getAtletas().get(0).isCapitao()).isTrue();
    }

    @Test
    @DisplayName("deve lancar 404 ao detalhar rodada inexistente")
    void deveLancar404AoDetalharRodadaInexistente() {
        when(repository.findByRodadaIdOrderById(99)).thenReturn(List.of());

        assertThatThrownBy(() -> service.buscarPorRodada(99))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private Time criarTime(int rodada) {
        var capitao = atleta(1, "Hulk", Posicao.ATA, 10.0, 8.54);
        var goleiro = atleta(3, "Weverton", Posicao.GOL, 12.0, 6.0);
        var reservaLuxo = atleta(2, "Cano", Posicao.ATA, 18.0, 7.9);

        Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
        titulares.put(Posicao.ATA, List.of(capitao));
        titulares.put(Posicao.GOL, List.of(goleiro));

        Map<Posicao, Atleta> reservas = new EnumMap<>(Posicao.class);
        reservas.put(Posicao.ATA, reservaLuxo);

        return Time.builder()
                .rodada(rodada)
                .avisoMercado(null)
                .titulares(titulares)
                .reservas(reservas)
                .capitao(capitao)
                .reservaLuxo(reservaLuxo)
                .alertasDuvida(List.of())
                .custoTotal(40.0)
                .build();
    }

    private Atleta atleta(int id, String apelido, Posicao posicao, double preco, double score) {
        return Atleta.builder()
                .atletaId(id).apelido(apelido).posicao(posicao)
                .clubeId(1).nomeClube("Atletico MG").siglaClube("ATM").nomeClubeNorm("atletico mg")
                .status(StatusAtleta.PROVAVEL).mediaPontos(score).valorizacao(0.0)
                .preco(preco).score(score).build();
    }

    private EscalacaoRodada registro(int rodada, int atletaId, String apelido, boolean capitao) {
        var e = new EscalacaoRodada();
        e.setRodadaId(rodada);
        e.setAtletaId(atletaId);
        e.setApelido(apelido);
        e.setPosicao("ATA");
        e.setClube("Atletico MG");
        e.setScoreSugerido(8.0);
        e.setPreco(20.0);
        e.setCapitao(capitao);
        e.setCriadoEm(java.time.LocalDateTime.now());
        return e;
    }

    private MercadoStatusResponse status(int rodadaAtual) {
        var s = new MercadoStatusResponse();
        s.setRodadaAtual(rodadaAtual);
        return s;
    }

    private PontuadosResponse pontuados(Map<Integer, Double> pontos) {
        var response = new PontuadosResponse();
        Map<String, PontuadosResponse.AtletaPontuado> mapa = new java.util.HashMap<>();
        pontos.forEach((id, pontuacao) -> {
            var ap = new PontuadosResponse.AtletaPontuado();
            ap.setAtletaId(id);
            ap.setPontuacaoNum(pontuacao);
            mapa.put(String.valueOf(id), ap);
        });
        response.setAtletas(mapa);
        return response;
    }
}
