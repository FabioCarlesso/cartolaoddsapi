package com.cartola.odds.service;

import com.cartola.odds.client.OddsClient;
import com.cartola.odds.config.OddsProperties;
import com.cartola.odds.model.OddsCotaHistorico;
import com.cartola.odds.repository.OddsCotaHistoricoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OddsCotaService")
class OddsCotaServiceTest {

    @Mock OddsClient                  oddsClient;
    @Mock OddsCotaHistoricoRepository historicoRepository;

    @Test
    @DisplayName("deve montar a resposta com os dados atuais do OddsClient")
    void deveMontarRespostaComDadosAtuais() {
        var agora = LocalDateTime.now();
        when(oddsClient.getRequestsRemaining()).thenReturn(412L);
        when(oddsClient.getRequestsUsed()).thenReturn(88L);
        when(oddsClient.getUltimaLeitura()).thenReturn(agora);
        when(oddsClient.isGuardrailAtivo()).thenReturn(false);

        var oddsCotaService = new OddsCotaService(oddsClient, propriedadesComMinimo(50), historicoRepository);
        var resposta = oddsCotaService.buscarCota();

        assertThat(resposta.getSaldoRestante()).isEqualTo(412L);
        assertThat(resposta.getConsumoMes()).isEqualTo(88L);
        assertThat(resposta.getUltimaLeitura()).isEqualTo(agora);
        assertThat(resposta.getMinRequestsRemaining()).isEqualTo(50);
        assertThat(resposta.isGuardrailAtivo()).isFalse();
    }

    @Test
    @DisplayName("deve dizer quando a proxima sondagem destrava o guardrail")
    void deveExporJanelaDeSondagem() {
        // Com guardrailAtivo=true, essa e a unica pergunta que sobra para quem esta olhando:
        // quando isso volta sozinho. Sem o campo, a resposta so existia no log.
        var sondagem = LocalDateTime.now().minusHours(2);
        var proxima  = sondagem.plusHours(24);
        when(oddsClient.getRequestsRemaining()).thenReturn(10L);
        when(oddsClient.getRequestsUsed()).thenReturn(490L);
        when(oddsClient.isGuardrailAtivo()).thenReturn(true);
        when(oddsClient.getUltimaSondagem()).thenReturn(sondagem);
        when(oddsClient.getProximaSondagem()).thenReturn(proxima);

        var resposta = new OddsCotaService(oddsClient, propriedadesComMinimo(50), historicoRepository).buscarCota();

        assertThat(resposta.isGuardrailAtivo()).isTrue();
        assertThat(resposta.getUltimaSondagem()).isEqualTo(sondagem);
        assertThat(resposta.getProximaSondagem()).isEqualTo(proxima);
    }

    @Test
    @DisplayName("deve refletir guardrail ativo e valores nulos sem leitura ainda")
    void deveRefletirSemLeitura() {
        when(oddsClient.getRequestsRemaining()).thenReturn(null);
        when(oddsClient.getRequestsUsed()).thenReturn(null);
        when(oddsClient.getUltimaLeitura()).thenReturn(null);
        when(oddsClient.isGuardrailAtivo()).thenReturn(false);

        var oddsCotaService = new OddsCotaService(oddsClient, propriedadesComMinimo(50), historicoRepository);
        var resposta = oddsCotaService.buscarCota();

        assertThat(resposta.getSaldoRestante()).isNull();
        assertThat(resposta.getConsumoMes()).isNull();
        assertThat(resposta.getUltimaLeitura()).isNull();
        assertThat(resposta.getUltimaSondagem()).isNull();
        assertThat(resposta.getProximaSondagem()).isNull();
    }

    @Nested
    @DisplayName("historico")
    class Historico {

        @Test
        @DisplayName("deve devolver as leituras da janela em ordem cronologica")
        void deveDevolverLeiturasDaJanela() {
            var ontem = LocalDateTime.now().minusDays(1);
            when(historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(any()))
                    .thenReturn(List.of(leitura(ontem, 412L, 88L), leitura(ontem.plusHours(1), 411L, 89L)));

            var resposta = servico().buscarHistorico(30);

            assertThat(resposta.getDias()).isEqualTo(30);
            assertThat(resposta.getTotal()).isEqualTo(2);
            assertThat(resposta.getDesde()).isNotNull();
            assertThat(resposta.getLeituras())
                    .extracting(l -> l.getSaldoRestante(), l -> l.getConsumoMes())
                    .containsExactly(tuple(412L, 88L), tuple(411L, 89L));
        }

        @Test
        @DisplayName("deve marcar reinicio de cota quando o consumo cai entre duas leituras")
        void deveMarcarReinicioDeCota() {
            // A virada do ciclo derruba o consumo e devolve o saldo. Sem a marca, quem desenha
            // o grafico le essa queda como falha de coleta e quebra a linha no lugar errado.
            var base = LocalDateTime.now().minusDays(2);
            when(historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(any()))
                    .thenReturn(List.of(
                            leitura(base, 8L, 492L),
                            leitura(base.plusHours(1), 500L, 0L),
                            leitura(base.plusHours(2), 499L, 1L)));

            var leituras = servico().buscarHistorico(30).getLeituras();

            assertThat(leituras).extracting(l -> l.isReinicioDeCota())
                    .containsExactly(false, true, false);
        }

        @Test
        @DisplayName("nao deve marcar reinicio na primeira leitura da janela")
        void naoDeveMarcarReinicioNaPrimeira() {
            // Sem uma leitura anterior para comparar, afirmar que a cota renovou seria chute.
            when(historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(any()))
                    .thenReturn(List.of(leitura(LocalDateTime.now(), 500L, 0L)));

            assertThat(servico().buscarHistorico(30).getLeituras())
                    .singleElement()
                    .satisfies(l -> assertThat(l.isReinicioDeCota()).isFalse());
        }

        @Test
        @DisplayName("leitura sem consumo nao deve quebrar a comparacao com a anterior")
        void leituraSemConsumoNaoQuebraComparacao() {
            // Uma resposta pode trazer so um dos dois headers. A referencia segue sendo o
            // ultimo consumo conhecido — senao um buraco no meio da serie inventaria um
            // reinicio na leitura seguinte.
            var base = LocalDateTime.now().minusDays(1);
            when(historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(any()))
                    .thenReturn(List.of(
                            leitura(base, 400L, 100L),
                            leitura(base.plusHours(1), 399L, null),
                            leitura(base.plusHours(2), 398L, 102L)));

            assertThat(servico().buscarHistorico(30).getLeituras())
                    .extracting(l -> l.isReinicioDeCota())
                    .containsExactly(false, false, false);
        }

        @Test
        @DisplayName("deve marcar reinicio quando o saldo sobe sem o consumo cair")
        void deveMarcarReinicioPeloSaldo() {
            // A renovacao mexe nos dois numeros: o consumo cai e o saldo sobe. Olhar so o
            // consumo perderia o ciclo que terminou com consumo baixissimo — aqui, 2 leituras
            // no mes inteiro. O saldo subindo denuncia a renovacao de qualquer jeito.
            var base = LocalDateTime.now().minusDays(2);
            when(historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(any()))
                    .thenReturn(List.of(
                            leitura(base, 498L, 2L),
                            leitura(base.plusHours(1), 500L, 2L)));

            assertThat(servico().buscarHistorico(30).getLeituras())
                    .extracting(l -> l.isReinicioDeCota())
                    .containsExactly(false, true);
        }

        @Test
        @DisplayName("saldo caindo no uso normal nao deve marcar reinicio")
        void saldoCaindoNaoMarcaReinicio() {
            var base = LocalDateTime.now().minusDays(1);
            when(historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(any()))
                    .thenReturn(List.of(
                            leitura(base, 400L, 100L),
                            leitura(base.plusHours(1), 399L, 101L),
                            leitura(base.plusHours(2), 398L, 102L)));

            assertThat(servico().buscarHistorico(30).getLeituras())
                    .extracting(l -> l.isReinicioDeCota())
                    .containsExactly(false, false, false);
        }

        @Test
        @DisplayName("deve recusar janela fora de 1..92 com IllegalArgumentException")
        void deveRecusarJanelaInvalida() {
            // O GlobalExceptionHandler traduz para 400: um ?dias=0 vindo da barra de endereco
            // nao pode virar 500, nem uma resposta de centenas de KB.
            assertThatThrownBy(() -> servico().buscarHistorico(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maior que 0");
            assertThatThrownBy(() -> servico().buscarHistorico(93))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no maximo 92");
        }

        @Test
        @DisplayName("deve devolver desde com a mesma precisao dos instantes da serie")
        void desdeDeveTerPrecisaoDeMicros() {
            // O PostgreSQL guarda `timestamp` em microssegundos; sem truncar, `desde` sairia com
            // nanossegundos do relogio da JVM e o payload teria duas precisoes diferentes.
            when(historicoRepository.findByInstanteGreaterThanEqualOrderByInstanteAscIdAsc(any()))
                    .thenReturn(List.of());

            assertThat(servico().buscarHistorico(30).getDesde().getNano() % 1000).isZero();
        }

        private OddsCotaService servico() {
            return new OddsCotaService(oddsClient, propriedadesComMinimo(50), historicoRepository);
        }

        private OddsCotaHistorico leitura(LocalDateTime instante, Long saldo, Long consumo) {
            var registro = new OddsCotaHistorico();
            registro.setInstante(instante);
            registro.setSaldoRestante(saldo);
            registro.setConsumoMes(consumo);
            return registro;
        }
    }

    private OddsProperties propriedadesComMinimo(int minimo) {
        var props = new OddsProperties();
        props.setMinRequestsRemaining(minimo);
        return props;
    }
}
