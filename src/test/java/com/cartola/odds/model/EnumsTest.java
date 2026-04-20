package com.cartola.odds.model;

import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Enums")
class EnumsTest {

    @Nested
    @DisplayName("Posicao")
    class PosicaoTest {

        @ParameterizedTest(name = "id={0} deve mapear para {1}")
        @CsvSource({ "1,GOL", "2,LAT", "3,ZAG", "4,MEI", "5,ATA", "6,TEC" })
        @DisplayName("fromId deve mapear todos os ids validos")
        void fromIdDeveMapearIdsValidos(int id, String sigla) {
            assertThat(Posicao.fromId(id))
                    .isPresent()
                    .hasValueSatisfying(p -> assertThat(p.name()).isEqualTo(sigla));
        }

        @Test
        @DisplayName("fromId deve retornar vazio para id desconhecido")
        void fromIdDeveRetornarVazioParaIdInvalido() {
            assertThat(Posicao.fromId(99)).isEmpty();
            assertThat(Posicao.fromId(0)).isEmpty();
        }

        @ParameterizedTest(name = "sigla={0}")
        @CsvSource({ "GOL", "LAT", "ZAG", "MEI", "ATA", "TEC" })
        @DisplayName("fromSigla deve encontrar posicao por sigla (case insensitive)")
        void fromSiglaDeveEncontrarPosicao(String sigla) {
            assertThat(Posicao.fromSigla(sigla)).isPresent();
            assertThat(Posicao.fromSigla(sigla.toLowerCase())).isPresent();
        }

        @Test
        @DisplayName("fromSigla deve retornar vazio para sigla desconhecida")
        void fromSiglaDeveRetornarVazioParaSiglaInvalida() {
            assertThat(Posicao.fromSigla("DEF")).isEmpty();
            assertThat(Posicao.fromSigla("???")).isEmpty();
        }

        @Test
        @DisplayName("todas as posicoes devem ter label preenchido")
        void todasPosicoesDevemTerLabel() {
            for (Posicao p : Posicao.values()) {
                assertThat(p.getLabel()).isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("StatusAtleta")
    class StatusAtletaTest {

        @ParameterizedTest(name = "id={0} -> escalavel={1}")
        @CsvSource({ "2,false", "3,false", "5,false", "6,true", "7,true" })
        @DisplayName("deve identificar corretamente quais status sao escalaveis")
        void deveIdentificarEscalaveis(int id, boolean escalavel) {
            assertThat(StatusAtleta.fromId(id))
                    .isPresent()
                    .hasValueSatisfying(s -> assertThat(s.isEscalavel()).isEqualTo(escalavel));
        }

        @Test
        @DisplayName("fromId deve retornar vazio para id desconhecido")
        void fromIdDeveRetornarVazioParaIdInvalido() {
            assertThat(StatusAtleta.fromId(99)).isEmpty();
            assertThat(StatusAtleta.fromId(0)).isEmpty();
        }

        @Test
        @DisplayName("idsEscalaveis deve conter exatamente DUVIDA e PROVAVEL")
        void idsEscalaveisDeveConterDuvidaEProvavel() {
            var ids = StatusAtleta.idsEscalaveis();
            assertThat(ids).containsExactlyInAnyOrder(6, 7);
        }

        @Test
        @DisplayName("todos os status devem ter label preenchido")
        void todosStatusDevemTerLabel() {
            for (StatusAtleta s : StatusAtleta.values()) {
                assertThat(s.getLabel()).isNotBlank();
            }
        }
    }
}
