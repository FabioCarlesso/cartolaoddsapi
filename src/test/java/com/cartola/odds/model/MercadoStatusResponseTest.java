package com.cartola.odds.model;

import com.cartola.odds.model.enums.StatusMercado;
import com.cartola.odds.model.response.MercadoStatusResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MercadoStatusResponse")
class MercadoStatusResponseTest {

    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("deve retornar ABERTO quando statusMercado=1")
        void deveRetornarAbertoPara1() {
            var resp = response(1, 15);
            assertThat(resp.getStatus()).isEqualTo(StatusMercado.ABERTO);
        }

        @Test
        @DisplayName("deve retornar FECHADO quando statusMercado=2")
        void deveRetornarFechadoPara2() {
            var resp = response(2, 15);
            assertThat(resp.getStatus()).isEqualTo(StatusMercado.FECHADO);
        }

        @Test
        @DisplayName("deve retornar DESCONHECIDO para codigo nao mapeado")
        void deveRetornarDesconhecidoPara99() {
            var resp = response(99, 15);
            assertThat(resp.getStatus()).isEqualTo(StatusMercado.DESCONHECIDO);
        }
    }

    @Nested
    @DisplayName("isAberto")
    class IsAberto {

        @Test
        @DisplayName("deve retornar true quando statusMercado=1")
        void deveRetornarTruePara1() {
            assertThat(response(1, 15).isAberto()).isTrue();
        }

        @ParameterizedTest(name = "statusMercado={0} deve ser fechado")
        @CsvSource({"2", "3", "4", "6", "99"})
        @DisplayName("deve retornar false para qualquer status diferente de 1")
        void deveRetornarFalseParaOutrosStatus(int codigo) {
            assertThat(response(codigo, 15).isAberto()).isFalse();
        }
    }

    @Nested
    @DisplayName("getAvisoMercado")
    class GetAvisoMercado {

        @Test
        @DisplayName("deve retornar null quando mercado aberto")
        void deveRetornarNullQuandoAberto() {
            assertThat(response(1, 15).getAvisoMercado()).isNull();
        }

        @Test
        @DisplayName("deve retornar aviso quando mercado fechado (statusMercado=2)")
        void deveRetornarAvisoQuandoFechado() {
            var aviso = response(2, 15).getAvisoMercado();
            assertThat(aviso).isNotBlank();
            assertThat(aviso).containsIgnoringCase("rodada");
        }

        @Test
        @DisplayName("deve retornar aviso quando em manutencao (statusMercado=3)")
        void deveRetornarAvisoQuandoManutencao() {
            assertThat(response(3, 15).getAvisoMercado()).isNotBlank();
        }

        @Test
        @DisplayName("deve retornar aviso quando mercado parcial (statusMercado=4)")
        void deveRetornarAvisoQuandoParcial() {
            assertThat(response(4, 15).getAvisoMercado()).isNotBlank();
        }

        @Test
        @DisplayName("deve retornar aviso quando codigo desconhecido")
        void deveRetornarAvisoParaCodigoDesconhecido() {
            assertThat(response(99, 15).getAvisoMercado()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("rodadaAtual")
    class RodadaAtual {

        @Test
        @DisplayName("deve manter a rodada original independente do status")
        void deveMantarRodada() {
            assertThat(response(1, 22).getRodadaAtual()).isEqualTo(22);
            assertThat(response(2, 10).getRodadaAtual()).isEqualTo(10);
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private MercadoStatusResponse response(int statusMercado, int rodada) {
        var resp = new MercadoStatusResponse();
        resp.setStatusMercado(statusMercado);
        resp.setRodadaAtual(rodada);
        return resp;
    }
}
