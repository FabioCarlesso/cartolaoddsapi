package com.cartola.odds.service;

import com.cartola.odds.client.CartolaClient;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.model.response.AtletaResponse;
import com.cartola.odds.model.response.ClubeResponse;
import com.cartola.odds.util.NormalizadorUtil;
import com.cartola.odds.model.response.MercadoStatusResponse;
import com.cartola.odds.model.response.PartidaResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartolaDataService")
class CartolaDataServiceTest {

    @Mock  CartolaClient cartolaClient;
    @InjectMocks CartolaDataService service;

    @Nested
    @DisplayName("buscarStatusMercado")
    class BuscarStatusMercado {

        @Test
        @DisplayName("deve retornar status aberto quando status_mercado=1")
        void deveRetornarMercadoAberto() {
            var status = new MercadoStatusResponse();
            status.setStatusMercado(1);
            status.setRodadaAtual(15);
            when(cartolaClient.buscarStatusMercado()).thenReturn(status);

            var resultado = service.buscarStatusMercado();

            assertThat(resultado.isAberto()).isTrue();
            assertThat(resultado.getRodadaAtual()).isEqualTo(15);
        }

        @Test
        @DisplayName("deve retornar status fechado quando status_mercado diferente de 1")
        void deveRetornarMercadoFechado() {
            var status = new MercadoStatusResponse();
            status.setStatusMercado(2);
            when(cartolaClient.buscarStatusMercado()).thenReturn(status);

            assertThat(service.buscarStatusMercado().isAberto()).isFalse();
        }
    }

    @Nested
    @DisplayName("buscarAtletasFiltrados")
    class BuscarAtletasFiltrados {

        @Test
        @DisplayName("deve incluir atleta provavel com preco positivo do time favorito")
        void deveIncluirAtletaValido() {
            configurarMocks(atletaItem(1, 5, 7, 10.0, 8.0, 15.0));  // posicao=ATA, status=PROVAVEL

            var resultado = service.buscarAtletasFiltrados(Set.of("flamengo"));

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getPosicao()).isEqualTo(Posicao.ATA);
            assertThat(resultado.get(0).getStatus()).isEqualTo(StatusAtleta.PROVAVEL);
        }

        @Test
        @DisplayName("deve excluir atleta com status nao escalavel (contundido)")
        void deveExcluirContundido() {
            configurarMocks(atletaItem(1, 5, 2, 10.0, 8.0, 15.0));  // status=CONTUNDIDO

            assertThat(service.buscarAtletasFiltrados(Set.of("flamengo"))).isEmpty();
        }

        @Test
        @DisplayName("deve excluir atleta com status nao escalavel (suspenso)")
        void deveExcluirSuspenso() {
            configurarMocks(atletaItem(1, 5, 3, 10.0, 8.0, 15.0));  // status=SUSPENSO

            assertThat(service.buscarAtletasFiltrados(Set.of("flamengo"))).isEmpty();
        }

        @Test
        @DisplayName("deve excluir atleta com preco zero")
        void deveExcluirAtletaSemPreco() {
            configurarMocks(atletaItem(1, 5, 7, 0.0, 8.0, 0.0));    // preco=0

            assertThat(service.buscarAtletasFiltrados(Set.of("flamengo"))).isEmpty();
        }

        @Test
        @DisplayName("deve excluir atleta cujo time nao e favorito")
        void deveExcluirAtletaDeTimeNaoFavorito() {
            configurarMocks(atletaItem(1, 5, 7, 10.0, 8.0, 15.0));  // clube flamengo

            // favoritos nao contem flamengo
            assertThat(service.buscarAtletasFiltrados(Set.of("palmeiras"))).isEmpty();
        }

        @Test
        @DisplayName("deve incluir atleta de qualquer time quando favoritos estiver vazio")
        void deveIncluirTodosQuandoSemFavoritos() {
            configurarMocks(atletaItem(1, 5, 7, 10.0, 8.0, 15.0));

            // sem favoritos = filtro desativado
            var resultado = service.buscarAtletasFiltrados(Set.of());

            assertThat(resultado).hasSize(1);
        }

        @Test
        @DisplayName("deve incluir atleta em duvida (status 6)")
        void deveIncluirAtletaEmDuvida() {
            configurarMocks(atletaItem(1, 5, 6, 10.0, 8.0, 15.0));  // status=DUVIDA

            var resultado = service.buscarAtletasFiltrados(Set.of("flamengo"));

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getStatus()).isEqualTo(StatusAtleta.DUVIDA);
        }

        @Test
        @DisplayName("deve cruzar favoritos usando aliases do nome do clube")
        void deveCruzarFavoritosUsandoAliases() {
            configurarMocksComNomeClube(atletaItem(1, 5, 7, 10.0, 8.0, 15.0), "Fluminense FC");

            var resultado = service.buscarAtletasFiltrados(Set.of("fluminense"));

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getNomeClubeNorm()).isEqualTo("fluminense");
        }

        @Test
        @DisplayName("deve normalizar o clube pelo slug e preservar a sigla na exibicao")
        void deveNormalizarPeloSlugPreservandoExibicao() {
            configurarMocksComSlug(atletaItem(1, 5, 7, 10.0, 8.0, 15.0), "CAP", "atletico-pr");

            var resultado = service.buscarAtletasFiltrados(Set.of("athletico pr"));

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getNomeClubeNorm()).isEqualTo("athletico pr");
            assertThat(resultado.get(0).getNomeClube()).isEqualTo("CAP");
        }

        @Test
        @DisplayName("deve usar primeiras 3 letras do nome do clube como sigla quando abreviacao ausente")
        void deveUsarFallbackDeSigla() {
            configurarMocksComClubeSemAbreviacao(atletaItem(1, 5, 7, 10.0, 8.0, 15.0));

            var resultado = service.buscarAtletasFiltrados(Set.of());

            assertThat(resultado.get(0).getSiglaClube()).isEqualTo("FLA");
        }

        @Test
        @DisplayName("deve mapear posicao_id corretamente para enum Posicao")
        void deveMapearPosicaoCorretamente() {
            configurarMocks(atletaItem(1, 1, 7, 10.0, 8.0, 15.0));  // posicao=GOL

            var resultado = service.buscarAtletasFiltrados(Set.of());

            assertThat(resultado.get(0).getPosicao()).isEqualTo(Posicao.GOL);
        }

        @Test
        @DisplayName("deve mapear scouts e tratar valores ausentes ou nulos como zero")
        void deveMapearScoutsComFallbackZero() {
            var item = atletaItem(1, 1, 7, 10.0, 8.0, 15.0);
            var scout = new HashMap<String, Integer>();
            scout.put("DD", 4);
            scout.put("GS", null);
            scout.put("DP", 1);
            item.setScout(scout);
            configurarMocks(item);

            var resultado = service.buscarAtletasFiltrados(Set.of());

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getDefesasDificeis()).isEqualTo(4);
            assertThat(resultado.get(0).getGolsSofridos()).isZero();
            assertThat(resultado.get(0).getPenaltisDefendidos()).isEqualTo(1);
            assertThat(resultado.get(0).getGols()).isZero();
            assertThat(resultado.get(0).getAssistencias()).isZero();
        }

        @Test
        @DisplayName("deve ignorar atleta com posicao_id desconhecida")
        void deveIgnorarPosicaoDesconhecida() {
            configurarMocks(atletaItem(1, 99, 7, 10.0, 8.0, 15.0));  // posicao_id invalido

            assertThat(service.buscarAtletasFiltrados(Set.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("buscarTimesCasa")
    class BuscarTimesCasa {

        @Test
        @DisplayName("deve retornar set com IDs dos times mandantes")
        void deveRetornarTimesCasa() {
            var partida = new PartidaResponse.PartidaItem();
            partida.setClubeCasaId(10);
            partida.setClubeVisitanteId(20);
            var partidaResp = new PartidaResponse();
            partidaResp.setPartidas(List.of(partida));
            when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);

            assertThat(service.buscarTimesCasa()).containsExactly(10);
        }

        @Test
        @DisplayName("deve retornar set vazio quando sem partidas")
        void deveRetornarVazioSemPartidas() {
            var resp = new PartidaResponse();
            resp.setPartidas(List.of());
            when(cartolaClient.buscarPartidas()).thenReturn(resp);

            assertThat(service.buscarTimesCasa()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buscarConfrontosRodadaAtual")
    class BuscarConfrontosRodadaAtual {

        @Test
        @DisplayName("deve retornar chaves normalizadas dos confrontos da rodada")
        void deveRetornarConfrontosNormalizados() {
            var partida = partida(1, 2);
            var partidaResp = new PartidaResponse();
            partidaResp.setPartidas(List.of(partida));

            when(cartolaClient.buscarClubes()).thenReturn(Map.of(
                    "1", clube("Flamengo", "FLA"),
                    "2", clube("Botafogo FR", "BOT")
            ));
            when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);

            assertThat(service.buscarConfrontosRodadaAtual()).containsExactlyInAnyOrder("botafogo|flamengo");
        }

        @Test
        @DisplayName("deve montar a chave pelo slug quando nome e nome_fantasia vierem como sigla")
        void deveMontarChavePeloSlug() {
            var partidaResp = new PartidaResponse();
            partidaResp.setPartidas(List.of(partida(1, 2)));

            when(cartolaClient.buscarClubes()).thenReturn(Map.of(
                    "1", clubeComSlug("RBB", "RBB", "bragantino"),
                    "2", clubeComSlug("BAH", "BAH", "bahia")
            ));
            when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);

            assertThat(service.buscarConfrontosRodadaAtual()).containsExactlyInAnyOrder("bahia|bragantino");
        }

        @Test
        @DisplayName("deve casar o slug atletico-pr com a grafia Athletico da Odds API")
        void deveCasarGrafiaAthletico() {
            var partidaResp = new PartidaResponse();
            partidaResp.setPartidas(List.of(partida(1, 2)));

            when(cartolaClient.buscarClubes()).thenReturn(Map.of(
                    "1", clubeComSlug("CRU", "CRU", "cruzeiro"),
                    "2", clubeComSlug("CAP", "CAP", "atletico-pr")
            ));
            when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);

            assertThat(service.buscarConfrontosRodadaAtual())
                    .containsExactlyInAnyOrder(NormalizadorUtil.chaveConfronto("Cruzeiro", "Athletico Paranaense"));
        }

        @Test
        @DisplayName("deve retornar vazio quando nao houver partidas")
        void deveRetornarVazioSemPartidas() {
            var partidaResp = new PartidaResponse();
            partidaResp.setPartidas(List.of());

            when(cartolaClient.buscarClubes()).thenReturn(Map.of());
            when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);

            assertThat(service.buscarConfrontosRodadaAtual()).isEmpty();
        }

        @Test
        @DisplayName("deve descartar silenciosamente partida cujo clube nao existe no mapa de clubes")
        void deveDescartarPartidaComClubeDesconhecido() {
            var partida = partida(99, 2);
            var partidaResp = new PartidaResponse();
            partidaResp.setPartidas(List.of(partida));

            when(cartolaClient.buscarClubes()).thenReturn(Map.of(
                    "2", clube("Botafogo FR", "BOT")
            ));
            when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);

            assertThat(service.buscarConfrontosRodadaAtual()).isEmpty();
        }

        @Test
        @DisplayName("deve propagar excecao quando o cliente Cartola falhar")
        void devePropagarExcecaoDoCliente() {
            when(cartolaClient.buscarClubes()).thenThrow(new RuntimeException("Cartola indisponivel"));

            assertThrows(RuntimeException.class, () -> service.buscarConfrontosRodadaAtual());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void configurarMocks(AtletaResponse.AtletaItem item) {
        var clube = new ClubeResponse();
        clube.setNome("Flamengo");
        clube.setAbreviacao("FLA");

        var atletaResp = new AtletaResponse();
        atletaResp.setAtletas(List.of(item));

        var partida = new PartidaResponse.PartidaItem();
        partida.setClubeCasaId(1);
        var partidaResp = new PartidaResponse();
        partidaResp.setPartidas(List.of(partida));

        when(cartolaClient.buscarClubes()).thenReturn(Map.of("1", clube));
        when(cartolaClient.buscarAtletas()).thenReturn(atletaResp);
        when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);
    }

    private void configurarMocksComClubeSemAbreviacao(AtletaResponse.AtletaItem item) {
        var clube = new ClubeResponse();
        clube.setNome("Flamengo");
        clube.setAbreviacao(null);  // sem abreviacao

        var atletaResp = new AtletaResponse();
        atletaResp.setAtletas(List.of(item));

        var partida = new PartidaResponse.PartidaItem();
        partida.setClubeCasaId(1);
        var partidaResp = new PartidaResponse();
        partidaResp.setPartidas(List.of(partida));

        when(cartolaClient.buscarClubes()).thenReturn(Map.of("1", clube));
        when(cartolaClient.buscarAtletas()).thenReturn(atletaResp);
        when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);
    }

    private void configurarMocksComNomeClube(AtletaResponse.AtletaItem item, String nomeClube) {
        var clube = new ClubeResponse();
        clube.setNome(nomeClube);
        clube.setAbreviacao(nomeClube.length() >= 3 ? nomeClube.substring(0, 3).toUpperCase() : null);

        var atletaResp = new AtletaResponse();
        atletaResp.setAtletas(List.of(item));

        var partida = new PartidaResponse.PartidaItem();
        partida.setClubeCasaId(1);
        var partidaResp = new PartidaResponse();
        partidaResp.setPartidas(List.of(partida));

        when(cartolaClient.buscarClubes()).thenReturn(Map.of("1", clube));
        when(cartolaClient.buscarAtletas()).thenReturn(atletaResp);
        when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);
    }

    private AtletaResponse.AtletaItem atletaItem(int clubeId, int posicaoId, int statusId,
                                                  double media, double variacao, double preco) {
        var item = new AtletaResponse.AtletaItem();
        item.setAtletaId(1);
        item.setApelido("Jogador Teste");
        item.setClubeId(clubeId);
        item.setPosicaoId(posicaoId);
        item.setStatusId(statusId);
        item.setMediaNum(media);
        item.setVariacaoNum(variacao);
        item.setPrecoNum(preco);
        return item;
    }

    private PartidaResponse.PartidaItem partida(int clubeCasaId, int clubeVisitanteId) {
        var partida = new PartidaResponse.PartidaItem();
        partida.setClubeCasaId(clubeCasaId);
        partida.setClubeVisitanteId(clubeVisitanteId);
        return partida;
    }

    private void configurarMocksComSlug(AtletaResponse.AtletaItem item, String sigla, String slug) {
        var clube = clubeComSlug(sigla, sigla, slug);

        var atletaResp = new AtletaResponse();
        atletaResp.setAtletas(List.of(item));

        var partida = new PartidaResponse.PartidaItem();
        partida.setClubeCasaId(1);
        var partidaResp = new PartidaResponse();
        partidaResp.setPartidas(List.of(partida));

        when(cartolaClient.buscarClubes()).thenReturn(Map.of("1", clube));
        when(cartolaClient.buscarAtletas()).thenReturn(atletaResp);
        when(cartolaClient.buscarPartidas()).thenReturn(partidaResp);
    }

    /** Formato atual do /partidas: sigla em nome e nome_fantasia, nome por extenso so no slug. */
    private ClubeResponse clubeComSlug(String nome, String abreviacao, String slug) {
        var clube = new ClubeResponse();
        clube.setNome(nome);
        clube.setNomeFantasia(nome);
        clube.setAbreviacao(abreviacao);
        clube.setSlug(slug);
        return clube;
    }

    private ClubeResponse clube(String nome, String abreviacao) {
        var clube = new ClubeResponse();
        clube.setNome(nome);
        clube.setAbreviacao(abreviacao);
        return clube;
    }
}
