package com.cartola.odds.model;

import com.cartola.odds.model.enums.StatusMercado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StatusMercado")
class StatusMercadoTest {

    @Nested
    @DisplayName("fromCodigo")
    class FromCodigo {

        @ParameterizedTest(name = "codigo {0} -> {1}")
        @CsvSource({
            "1, ABERTO",
            "2, FECHADO",
            "3, MANUTENCAO",
            "4, PARCIAL",
            "6, FINALIZANDO",
        })
        @DisplayName("deve mapear codigos conhecidos corretamente")
        void deveMapearCodigosConhecidos(int codigo, String esperado) {
            assertThat(StatusMercado.fromCodigo(codigo).name()).isEqualTo(esperado);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 5, 7, 99, -5})
        @DisplayName("deve retornar DESCONHECIDO para codigos invalidos")
        void deveRetornarDesconhecidoParaCodigosInvalidos(int codigo) {
            assertThat(StatusMercado.fromCodigo(codigo)).isEqualTo(StatusMercado.DESCONHECIDO);
        }
    }

    @Nested
    @DisplayName("isAberto")
    class IsAberto {

        @Test
        @DisplayName("deve retornar true somente para ABERTO")
        void deveSomentAbertoSerAberto() {
            assertThat(StatusMercado.ABERTO.isAberto()).isTrue();
        }

        @ParameterizedTest
        @CsvSource({"2", "3", "4", "6", "-1"})
        @DisplayName("deve retornar false para todos os outros status")
        void deveRetornarFalseParaOutros(int codigo) {
            assertThat(StatusMercado.fromCodigo(codigo).isAberto()).isFalse();
        }
    }

    @Nested
    @DisplayName("exibirAviso")
    class ExibirAviso {

        @Test
        @DisplayName("ABERTO nao deve exibir aviso")
        void abertoNaoDeveExibirAviso() {
            assertThat(StatusMercado.ABERTO.isExibirAviso()).isFalse();
        }

        @ParameterizedTest(name = "codigo {0} deve exibir aviso")
        @CsvSource({"2", "3", "4", "6", "-1"})
        @DisplayName("todos os status nao-abertos devem exibir aviso")
        void statusNaoAbertoDeveExibirAviso(int codigo) {
            assertThat(StatusMercado.fromCodigo(codigo).isExibirAviso()).isTrue();
        }
    }

    @Nested
    @DisplayName("aviso textual")
    class AvisoTextual {

        @Test
        @DisplayName("todos os status devem ter label preenchido")
        void todosDevemTerLabel() {
            for (var s : StatusMercado.values()) {
                assertThat(s.getLabel()).isNotBlank();
            }
        }

        @Test
        @DisplayName("todos os status devem ter aviso preenchido")
        void todosDevemTerAviso() {
            for (var s : StatusMercado.values()) {
                assertThat(s.getAviso()).isNotBlank();
            }
        }

        @Test
        @DisplayName("FECHADO deve ter aviso sobre rodada em andamento")
        void fechadoDeveAvisarRodadaEmAndamento() {
            assertThat(StatusMercado.FECHADO.getAviso()).containsIgnoringCase("rodada");
        }

        @Test
        @DisplayName("MANUTENCAO deve ter aviso sobre manutencao")
        void manutencaoDeveAvisarManutencao() {
            assertThat(StatusMercado.MANUTENCAO.getAviso()).containsIgnoringCase("manuten");
        }
    }
}
