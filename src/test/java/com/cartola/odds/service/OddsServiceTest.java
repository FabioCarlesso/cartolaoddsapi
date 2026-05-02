package com.cartola.odds.service;

import com.cartola.odds.client.OddsClient;
import com.cartola.odds.model.Configuracao;
import com.cartola.odds.model.response.OddsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OddsService")
class OddsServiceTest {

    @Mock OddsClient          oddsClient;
    @Mock ConfiguracaoService configuracaoService;
    @Mock CartolaDataService  cartolaDataService;

    @InjectMocks OddsService oddsService;

    // ═══════════════════════════════════════════════════════════════
    // buscarFavoritos (Set<String>) — contrato do pipeline
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buscarFavoritos — Set<String>")
    class BuscarFavoritosSet {

        @BeforeEach
        void setUp() {
            when(configuracaoService.buscarConfig()).thenReturn(Configuracao.defaults());
        }

        @Test
        @DisplayName("deve retornar set vazio quando API nao retorna odds")
        void deveRetornarVazioSemOdds() {
            when(oddsClient.buscarOdds()).thenReturn(List.of());
            assertThat(oddsService.buscarFavoritos()).isEmpty();
        }

        @Test
        @DisplayName("deve incluir nome normalizado do time favorito")
        void deveIncluirNomeNormalizado() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 2.10, "Palmeiras", 3.50)));

            assertThat(oddsService.buscarFavoritos()).contains("flamengo");
        }

        @Test
        @DisplayName("deve excluir time com odd acima do limite")
        void deveExcluirAcimaDoLimite() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Fortaleza", 3.20, "Bahia", 3.40)));

            assertThat(oddsService.buscarFavoritos()).isEmpty();
        }

        @Test
        @DisplayName("deve normalizar nome com acento e hifen")
        void deveNormalizarNome() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Atletico-MG", 1.95, "Santos FC", 4.20)));

            assertThat(oddsService.buscarFavoritos()).contains("atletico mg");
        }

        @Test
        @DisplayName("deve aplicar alias no nome do favorito")
        void deveAplicarAliasNoFavorito() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Inter", 1.95, "Fluminense FC", 4.20)));

            assertThat(oddsService.buscarFavoritos()).containsExactly("internacional");
        }

        @Test
        @DisplayName("deve processar multiplos jogos retornando todos os favoritos validos")
        void deveProcessarMultiplosJogos() {
            when(oddsClient.buscarOdds()).thenReturn(List.of(
                jogo("Flamengo",  2.10, "Botafogo", 3.50),
                jogo("Palmeiras", 1.85, "Santos",   4.20),
                jogo("Fortaleza", 3.30, "Bahia",    3.40)
            ));

            var favoritos = oddsService.buscarFavoritos();

            assertThat(favoritos).containsExactlyInAnyOrder("flamengo", "palmeiras");
        }

        @Test
        @DisplayName("deve considerar somente favoritos dos confrontos da rodada atual")
        void deveConsiderarSomenteConfrontosDaRodadaAtual() {
            when(oddsClient.buscarOdds()).thenReturn(List.of(
                jogo("Flamengo", 2.10, "Botafogo", 3.50),
                jogo("Palmeiras", 1.85, "Santos", 4.20)
            ));
            when(cartolaDataService.buscarConfrontosRodadaAtual())
                    .thenReturn(Set.of("botafogo|flamengo"));

            var favoritos = oddsService.buscarFavoritos();

            assertThat(favoritos).containsExactly("flamengo");
        }

        @Test
        @DisplayName("deve retornar set imutavel")
        void deveRetornarSetImutavel() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 2.10, "Palmeiras", 3.50)));

            var favoritos = oddsService.buscarFavoritos();

            org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> favoritos.add("teste")
            );
        }

        @Test
        @DisplayName("deve ignorar jogo sem bookmakers")
        void deveIgnorarJogoSemBookmakers() {
            var jogoSemBookmaker = new OddsResponse();
            jogoSemBookmaker.setBookmakers(List.of());
            when(oddsClient.buscarOdds()).thenReturn(List.of(jogoSemBookmaker));

            assertThat(oddsService.buscarFavoritos()).isEmpty();
        }

        @Test
        @DisplayName("deve manter processamento quando confrontos da rodada nao puderem ser buscados")
        void deveManterProcessamentoQuandoConfrontosIndisponiveis() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 2.10, "Palmeiras", 3.50)));
            doThrow(new IllegalStateException("Cartola indisponivel"))
                    .when(cartolaDataService).buscarConfrontosRodadaAtual();

            assertThat(oddsService.buscarFavoritos()).containsExactly("flamengo");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // buscarFavoritosDetalhado — endpoint /api/favoritos
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buscarFavoritosDetalhado")
    class BuscarFavoritosDetalhado {

        @Test
        @DisplayName("deve retornar response com totalJogos=0 quando API nao retorna odds")
        void deveRetornarVazioQuandoSemOdds() {
            when(oddsClient.buscarOdds()).thenReturn(List.of());

            var resultado = oddsService.buscarFavoritosDetalhado(3.0);

            assertThat(resultado.getTotalJogos()).isEqualTo(0);
            assertThat(resultado.getFavoritos()).isEmpty();
            assertThat(resultado.getDescartados()).isEmpty();
        }

        @Test
        @DisplayName("deve classificar jogo com odd <= limite como favorito")
        void deveClassificarComoFavoritoAbaixoDoLimite() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 2.10, "Palmeiras", 3.50)));

            var resultado = oddsService.buscarFavoritosDetalhado(3.0);

            assertThat(resultado.getTotalFavoritos()).isEqualTo(1);
            assertThat(resultado.getFavoritos().get(0).getTimeFavorito()).isEqualTo("Flamengo");
            assertThat(resultado.getFavoritos().get(0).getOddFavorito()).isEqualTo(2.10);
        }

        @Test
        @DisplayName("deve classificar jogo com odd > limite como descartado")
        void deveClassificarComoDescartadoAcimaDoLimite() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Fortaleza", 3.20, "Bahia", 3.40)));

            var resultado = oddsService.buscarFavoritosDetalhado(3.0);

            assertThat(resultado.getTotalDescartados()).isEqualTo(1);
            assertThat(resultado.getFavoritos()).isEmpty();
            assertThat(resultado.getDescartados().get(0).getTimeCasa()).isEqualTo("Fortaleza");
            assertThat(resultado.getDescartados().get(0).getMotivo()).contains("3.20");
        }

        @Test
        @DisplayName("deve identificar corretamente o time adversario")
        void deveIdentificarAdversario() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 2.10, "Palmeiras", 3.50)));

            var jogo = oddsService.buscarFavoritosDetalhado(3.0).getFavoritos().get(0);

            assertThat(jogo.getTimeFavorito()).isEqualTo("Flamengo");
            assertThat(jogo.getTimeAdversario()).isEqualTo("Palmeiras");
            assertThat(jogo.getOddAdversario()).isEqualTo(3.50);
        }

        @Test
        @DisplayName("deve marcar favoritoEmCasa=true quando favorito e time da casa")
        void deveMarcaFavoritoEmCasa() {
            // Flamengo = homeTeam, tem menor odd
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 1.90, "Botafogo", 4.00)));

            var jogo = oddsService.buscarFavoritosDetalhado(3.0).getFavoritos().get(0);

            assertThat(jogo.isFavoritoEmCasa()).isTrue();
        }

        @Test
        @DisplayName("deve marcar favoritoEmCasa=false quando favorito e visitante")
        void deveMarcaFavoritoForaDeCasa() {
            // Botafogo = awayTeam, tem menor odd
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 3.80, "Botafogo", 2.10)));

            var jogo = oddsService.buscarFavoritosDetalhado(3.0).getFavoritos().get(0);

            assertThat(jogo.getTimeFavorito()).isEqualTo("Botafogo");
            assertThat(jogo.isFavoritoEmCasa()).isFalse();
        }

        @Test
        @DisplayName("deve incluir odd do empate no resultado")
        void deveIncluirOddEmpate() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogoComEmpate("Flamengo", 1.90, "Botafogo", 4.00, 3.30)));

            var jogo = oddsService.buscarFavoritosDetalhado(3.0).getFavoritos().get(0);

            assertThat(jogo.getOddEmpate()).isEqualTo(3.30);
        }

        @Test
        @DisplayName("deve separar corretamente favoritos e descartados em multiplos jogos")
        void deveSepararFavoritosEDescartados() {
            when(oddsClient.buscarOdds()).thenReturn(List.of(
                jogo("Flamengo",  2.10, "Botafogo", 3.50),   // favorito
                jogo("Palmeiras", 1.85, "Santos",   4.20),   // favorito
                jogo("Fortaleza", 3.30, "Bahia",    3.40),   // descartado
                jogo("Gremio",    3.10, "Vasco",    3.20)    // descartado
            ));

            var resultado = oddsService.buscarFavoritosDetalhado(3.0);

            assertThat(resultado.getTotalJogos()).isEqualTo(4);
            assertThat(resultado.getTotalFavoritos()).isEqualTo(2);
            assertThat(resultado.getTotalDescartados()).isEqualTo(2);
            assertThat(resultado.getFavoritos()).hasSize(2);
            assertThat(resultado.getDescartados()).hasSize(2);
        }

        @Test
        @DisplayName("deve listar somente jogos da rodada atual no response detalhado")
        void deveListarSomenteJogosDaRodadaAtualNoDetalhado() {
            when(oddsClient.buscarOdds()).thenReturn(List.of(
                jogo("Flamengo", 2.10, "Botafogo", 3.50),
                jogo("Palmeiras", 1.85, "Santos", 4.20),
                jogo("Fortaleza", 3.30, "Bahia", 3.40)
            ));
            when(cartolaDataService.buscarConfrontosRodadaAtual())
                    .thenReturn(Set.of("botafogo|flamengo", "bahia|fortaleza"));

            var resultado = oddsService.buscarFavoritosDetalhado(3.0);

            assertThat(resultado.getTotalJogos()).isEqualTo(2);
            assertThat(resultado.getTotalFavoritos()).isEqualTo(1);
            assertThat(resultado.getTotalDescartados()).isEqualTo(1);
            assertThat(resultado.getFavoritos().get(0).getTimeFavorito()).isEqualTo("Flamengo");
            assertThat(resultado.getDescartados().get(0).getTimeCasa()).isEqualTo("Fortaleza");
        }

        @Test
        @DisplayName("deve refletir o oddLimite informado no response")
        void deveRefletirOddLimiteInformado() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Flamengo", 2.50, "Palmeiras", 3.50)));

            var comLimite25 = oddsService.buscarFavoritosDetalhado(2.5);
            var comLimite30 = oddsService.buscarFavoritosDetalhado(3.0);

            // Com limite 2.5: Flamengo (2.50) passa exatamente
            assertThat(comLimite25.getTotalFavoritos()).isEqualTo(1);
            assertThat(comLimite25.getOddLimite()).isEqualTo(2.5);

            // Com limite 3.0: Flamengo (2.50) tambem passa
            assertThat(comLimite30.getTotalFavoritos()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve incluir motivo legivel no jogo descartado")
        void deveIncluirMotivoNoDescarte() {
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogo("Fortaleza", 3.10, "Bahia", 3.50)));

            var descartado = oddsService.buscarFavoritosDetalhado(3.0)
                    .getDescartados().get(0);

            assertThat(descartado.getMotivo()).isNotBlank();
            assertThat(descartado.getMotivo()).contains("3.10");
            assertThat(descartado.getMotivo()).contains("3.0");
        }

        @Test
        @DisplayName("deve ignorar jogo sem bookmakers sem lancar excecao")
        void deveIgnorarJogoSemBookmakers() {
            var jogoSemBookmaker = new OddsResponse();
            jogoSemBookmaker.setBookmakers(List.of());
            when(oddsClient.buscarOdds())
                    .thenReturn(List.of(jogoSemBookmaker, jogo("Flamengo", 1.90, "Vasco", 4.0)));

            var resultado = oddsService.buscarFavoritosDetalhado(3.0);

            // Apenas o jogo valido e processado
            assertThat(resultado.getTotalFavoritos()).isEqualTo(1);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private OddsResponse jogo(String homeName, double oddHome,
                               String awayName, double oddAway) {
        return jogoComEmpate(homeName, oddHome, awayName, oddAway, 0.0);
    }

    private OddsResponse jogoComEmpate(String homeName, double oddHome,
                                        String awayName, double oddAway,
                                        double oddEmpate) {
        var o1 = new OddsResponse.Outcome(); o1.setName(homeName); o1.setPrice(oddHome);
        var o2 = new OddsResponse.Outcome(); o2.setName(awayName); o2.setPrice(oddAway);

        var outcomes = new java.util.ArrayList<OddsResponse.Outcome>();
        outcomes.add(o1);
        outcomes.add(o2);

        if (oddEmpate > 0) {
            var draw = new OddsResponse.Outcome(); draw.setName("Draw"); draw.setPrice(oddEmpate);
            outcomes.add(draw);
        }

        var market = new OddsResponse.Market(); market.setOutcomes(outcomes);
        var bookmaker = new OddsResponse.Bookmaker(); bookmaker.setMarkets(List.of(market));

        var resp = new OddsResponse();
        resp.setHomeTeam(homeName);
        resp.setAwayTeam(awayName);
        resp.setBookmakers(List.of(bookmaker));
        return resp;
    }
}
