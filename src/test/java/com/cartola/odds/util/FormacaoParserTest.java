package com.cartola.odds.util;

import com.cartola.odds.model.FormacaoConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FormacaoParser")
class FormacaoParserTest {

    @Nested
    @DisplayName("parse (formacao unica)")
    class Parse {

        @Test
        @DisplayName("deve converter '4-3-3' em FormacaoConfig(4,3,3): DEF=4 (LAT 2 + ZAG 2), totalLinhas = 10")
        void deveConverterFormacaoValida() {
            FormacaoConfig config = FormacaoParser.parse("4-3-3");

            assertThat(config.defensores()).isEqualTo(4);
            assertThat(config.laterais()).isEqualTo(2);
            assertThat(config.zagueiros()).isEqualTo(2);
            assertThat(config.meias()).isEqualTo(3);
            assertThat(config.atacantes()).isEqualTo(3);
            assertThat(config.totalLinhas()).isEqualTo(10);
            assertThat(config.label()).isEqualTo("4-3-3");
        }

        @Test
        @DisplayName("deve ignorar espacos ao redor da formacao")
        void deveIgnorarEspacos() {
            assertThat(FormacaoParser.parse(" 3-4-3 ")).isEqualTo(new FormacaoConfig(3, 4, 3));
        }

        @Test
        @DisplayName("deve rejeitar formacao com soma diferente de 10")
        void deveRejeitarSomaInvalida() {
            assertThatThrownBy(() -> FormacaoParser.parse("4-3-2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("10")
                    .hasMessageContaining("9");
        }

        @Test
        @DisplayName("deve rejeitar formacao com numero de partes diferente de 3")
        void deveRejeitarPartesInvalidas() {
            assertThatThrownBy(() -> FormacaoParser.parse("4-3"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("def-mei-ata");
        }

        @Test
        @DisplayName("deve rejeitar formacao com traco final (token vazio)")
        void deveRejeitarTracoFinal() {
            assertThatThrownBy(() -> FormacaoParser.parse("4-3-3-"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("def-mei-ata");
        }

        @Test
        @DisplayName("deve rejeitar formacao com posicao zerada")
        void deveRejeitarPosicaoZerada() {
            assertThatThrownBy(() -> FormacaoParser.parse("10-0-0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ao menos 1");
        }

        @Test
        @DisplayName("deve rejeitar formacao com valor nao numerico")
        void deveRejeitarValorNaoNumerico() {
            assertThatThrownBy(() -> FormacaoParser.parse("4-x-3"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("deve rejeitar formacao nula ou vazia")
        void deveRejeitarNulaOuVazia() {
            assertThatThrownBy(() -> FormacaoParser.parse(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FormacaoParser.parse("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("parseLista (multiplas formacoes)")
    class ParseLista {

        @Test
        @DisplayName("deve converter 2 formacoes validas e distintas")
        void deveConverterDuasValidas() {
            List<FormacaoConfig> formacoes = FormacaoParser.parseLista("3-4-3,4-3-3");

            assertThat(formacoes).containsExactly(
                    new FormacaoConfig(3, 4, 3),
                    new FormacaoConfig(4, 3, 3));
        }

        @Test
        @DisplayName("deve remover duplicatas preservando a ordem")
        void deveRemoverDuplicatas() {
            List<FormacaoConfig> formacoes = FormacaoParser.parseLista("4-3-3,4-3-3,3-4-3");

            assertThat(formacoes).containsExactly(
                    new FormacaoConfig(4, 3, 3),
                    new FormacaoConfig(3, 4, 3));
        }

        @Test
        @DisplayName("deve lancar excecao quando, apos remover duplicatas, sobra menos de 2 formacoes")
        void deveRejeitarMenosDeDuasAposDeduplicar() {
            assertThatThrownBy(() -> FormacaoParser.parseLista("4-3-3,4-3-3"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ao menos 2");
        }

        @Test
        @DisplayName("deve lancar excecao quando apenas 1 formacao e informada")
        void deveRejeitarUmaFormacao() {
            assertThatThrownBy(() -> FormacaoParser.parseLista("4-3-3"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ao menos 2");
        }

        @Test
        @DisplayName("deve lancar excecao quando mais de 5 formacoes distintas sao informadas")
        void deveRejeitarMaisDeCinco() {
            assertThatThrownBy(() ->
                    FormacaoParser.parseLista("4-3-3,3-4-3,4-4-2,5-3-2,4-5-1,3-5-2"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Maximo de 5");
        }

        @Test
        @DisplayName("deve lancar excecao quando alguma formacao da lista e invalida")
        void deveRejeitarFormacaoInvalidaNaLista() {
            assertThatThrownBy(() -> FormacaoParser.parseLista("4-3-2,3-4-3"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("4-3-2");
        }

        @Test
        @DisplayName("deve lancar excecao quando a lista e nula ou vazia")
        void deveRejeitarListaVazia() {
            assertThatThrownBy(() -> FormacaoParser.parseLista(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obrigatorio");
            assertThatThrownBy(() -> FormacaoParser.parseLista("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("deve aceitar 5 formacoes distintas")
        void deveAceitarCinco() {
            List<FormacaoConfig> formacoes =
                    FormacaoParser.parseLista("4-3-3,3-4-3,4-4-2,5-3-2,4-5-1");

            assertThat(formacoes).hasSize(5);
        }
    }
}
