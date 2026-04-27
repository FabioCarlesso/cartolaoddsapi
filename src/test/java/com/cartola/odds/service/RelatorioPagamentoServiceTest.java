package com.cartola.odds.service;

import com.cartola.odds.model.Atleta;
import com.cartola.odds.model.Time;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelatorioPagamentoService")
class RelatorioPagamentoServiceTest {

    @Mock PipelineService pipelineService;
    @InjectMocks RelatorioPagamentoService service;

    @Nested
    @DisplayName("gerar")
    class Gerar {

        @Test
        @DisplayName("deve incluir titulares, reservas e marcar capitao")
        void deveIncluirTodosOsItens() {
            var capitao = atleta(1, "Hulk",   Posicao.ATA, "ATM", 22.0);
            var meia    = atleta(2, "Cano",   Posicao.MEI, "FLU", 18.3);
            var reserva = atleta(3, "Reserva", Posicao.MEI, "FLA", 9.0);

            Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
            titulares.put(Posicao.ATA, List.of(capitao));
            titulares.put(Posicao.MEI, List.of(meia));

            var time = Time.builder()
                    .rodada(15).avisoMercado(null).titulares(titulares)
                    .reservas(Map.of(Posicao.MEI, reserva))
                    .capitao(capitao).reservaLuxo(reserva)
                    .alertasDuvida(List.of()).custoTotal(49.3).build();

            when(pipelineService.executar()).thenReturn(time);

            var relatorio = service.gerar();

            assertThat(relatorio.getRodada()).isEqualTo(15);
            assertThat(relatorio.getTotalProfissionais()).isEqualTo(3);
            assertThat(relatorio.getTotalPagamento()).isEqualTo(49.3);
            assertThat(relatorio.getMoeda()).isEqualTo("C$");
            assertThat(relatorio.getGeradoEm()).isNotNull();
            assertThat(relatorio.getItens())
                    .extracting("apelido")
                    .containsExactlyInAnyOrder("Hulk", "Cano", "Reserva");
        }

        @Test
        @DisplayName("deve ordenar itens por valor decrescente")
        void deveOrdenarPorValorDecrescente() {
            var caro    = atleta(1, "Caro",    Posicao.ATA, "ATM", 22.0);
            var medio   = atleta(2, "Medio",   Posicao.MEI, "FLU", 18.3);
            var barato  = atleta(3, "Barato",  Posicao.GOL, "FLA", 8.0);

            Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
            titulares.put(Posicao.ATA, List.of(caro));
            titulares.put(Posicao.MEI, List.of(medio));
            titulares.put(Posicao.GOL, List.of(barato));

            var time = Time.builder().rodada(15).avisoMercado(null)
                    .titulares(titulares).reservas(Map.of())
                    .capitao(caro).reservaLuxo(null)
                    .alertasDuvida(List.of()).custoTotal(48.3).build();

            when(pipelineService.executar()).thenReturn(time);

            var relatorio = service.gerar();

            assertThat(relatorio.getItens())
                    .extracting("apelido")
                    .containsExactly("Caro", "Medio", "Barato");
        }

        @Test
        @DisplayName("deve marcar funcao Capitao para o atleta capitao")
        void deveMarcarFuncaoCapitao() {
            var capitao = atleta(1, "Hulk", Posicao.ATA, "ATM", 22.0);
            var titularNormal = atleta(2, "Cano", Posicao.MEI, "FLU", 18.3);

            Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
            titulares.put(Posicao.ATA, List.of(capitao));
            titulares.put(Posicao.MEI, List.of(titularNormal));

            var time = Time.builder().rodada(10).avisoMercado(null)
                    .titulares(titulares).reservas(Map.of())
                    .capitao(capitao).reservaLuxo(null)
                    .alertasDuvida(List.of()).custoTotal(40.3).build();

            when(pipelineService.executar()).thenReturn(time);

            var relatorio = service.gerar();

            var hulk = relatorio.getItens().stream().filter(i -> i.getApelido().equals("Hulk")).findFirst().orElseThrow();
            var cano = relatorio.getItens().stream().filter(i -> i.getApelido().equals("Cano")).findFirst().orElseThrow();
            assertThat(hulk.isCapitao()).isTrue();
            assertThat(hulk.getFuncao()).isEqualTo("Capitao");
            assertThat(cano.isCapitao()).isFalse();
            assertThat(cano.getFuncao()).isEqualTo("Titular");
        }

        @Test
        @DisplayName("deve marcar funcao Reserva para reservas")
        void deveMarcarFuncaoReserva() {
            var capitao = atleta(1, "Hulk", Posicao.ATA, "ATM", 22.0);
            var reserva = atleta(2, "Reserva", Posicao.MEI, "FLA", 9.0);

            Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
            titulares.put(Posicao.ATA, List.of(capitao));

            var time = Time.builder().rodada(10).avisoMercado(null)
                    .titulares(titulares).reservas(Map.of(Posicao.MEI, reserva))
                    .capitao(capitao).reservaLuxo(reserva)
                    .alertasDuvida(List.of()).custoTotal(31.0).build();

            when(pipelineService.executar()).thenReturn(time);

            var relatorio = service.gerar();
            var item = relatorio.getItens().stream()
                    .filter(i -> i.getApelido().equals("Reserva"))
                    .findFirst().orElseThrow();
            assertThat(item.isTitular()).isFalse();
            assertThat(item.getFuncao()).isEqualTo("Reserva");
        }

        @Test
        @DisplayName("deve propagar avisoMercado do Time")
        void devePropagarAvisoMercado() {
            var capitao = atleta(1, "Hulk", Posicao.ATA, "ATM", 22.0);
            Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
            titulares.put(Posicao.ATA, List.of(capitao));

            var time = Time.builder().rodada(15)
                    .avisoMercado("Mercado fechado. Rodada em andamento.")
                    .titulares(titulares).reservas(Map.of())
                    .capitao(capitao).reservaLuxo(null)
                    .alertasDuvida(List.of()).custoTotal(22.0).build();

            when(pipelineService.executar()).thenReturn(time);

            var relatorio = service.gerar();
            assertThat(relatorio.getAvisoMercado())
                    .isEqualTo("Mercado fechado. Rodada em andamento.");
        }

        @Test
        @DisplayName("deve arredondar total de pagamento para duas casas")
        void deveArredondarTotal() {
            var a = atleta(1, "A", Posicao.ATA, "ATM", 10.111);
            var b = atleta(2, "B", Posicao.MEI, "FLU", 5.225);

            Map<Posicao, List<Atleta>> titulares = new EnumMap<>(Posicao.class);
            titulares.put(Posicao.ATA, List.of(a));
            titulares.put(Posicao.MEI, List.of(b));

            var time = Time.builder().rodada(1).avisoMercado(null)
                    .titulares(titulares).reservas(Map.of())
                    .capitao(a).reservaLuxo(null)
                    .alertasDuvida(List.of()).custoTotal(15.336).build();

            when(pipelineService.executar()).thenReturn(time);

            var relatorio = service.gerar();
            assertThat(relatorio.getTotalPagamento()).isEqualTo(15.34);
        }

        @Test
        @DisplayName("deve gerar relatorio sem itens quando time vier vazio")
        void deveSuportarTimeVazio() {
            var time = Time.builder().rodada(1).avisoMercado(null)
                    .titulares(new EnumMap<>(Posicao.class)).reservas(Map.of())
                    .capitao(null).reservaLuxo(null)
                    .alertasDuvida(List.of()).custoTotal(0.0).build();
            when(pipelineService.executar()).thenReturn(time);

            var relatorio = service.gerar();
            assertThat(relatorio.getTotalProfissionais()).isZero();
            assertThat(relatorio.getTotalPagamento()).isZero();
            assertThat(relatorio.getItens()).isEmpty();
        }
    }

    private static Atleta atleta(int id, String apelido, Posicao posicao, String sigla, double preco) {
        return Atleta.builder()
                .atletaId(id).apelido(apelido).posicao(posicao)
                .clubeId(id).nomeClube(sigla).siglaClube(sigla).nomeClubeNorm(sigla.toLowerCase())
                .status(StatusAtleta.PROVAVEL).mediaPontos(7.0).valorizacao(1.0)
                .preco(preco).desempenhoRecente(0.0).score(5.0).build();
    }
}
