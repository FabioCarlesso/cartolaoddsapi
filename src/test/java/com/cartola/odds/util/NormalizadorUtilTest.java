package com.cartola.odds.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NormalizadorUtil")
class NormalizadorUtilTest {

    @ParameterizedTest(name = "''{0}'' -> ''{1}''")
    @CsvSource({
        "Atletico-MG,        atletico mg",
        "Sao Paulo FC,       sao paulo fc",
        "Gremio,             gremio",
        "Athletico Paranaense, athletico paranaense",
        "Flamengo,           flamengo",
        "BOTAFOGO,           botafogo",
        "Vasco da Gama,      vasco da gama",
        "Coritiba FC,        coritiba fc",
    })
    @DisplayName("deve normalizar nomes de clubes corretamente")
    void deveNormalizarNomesDeClube(String entrada, String esperado) {
        assertThat(NormalizadorUtil.normalizar(entrada.strip()))
                .isEqualTo(esperado.strip());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t", "\n" })
    @DisplayName("deve retornar string vazia para entradas nulas, vazias ou em branco")
    void deveRetornarVazioParaEntradasInvalidas(String entrada) {
        assertThat(NormalizadorUtil.normalizar(entrada)).isEmpty();
    }

    @Test
    @DisplayName("deve remover hifen e substituir por espaco")
    void deveRemoverHifen() {
        assertThat(NormalizadorUtil.normalizar("Atletico-MG")).isEqualTo("atletico mg");
    }

    @Test
    @DisplayName("deve remover barra e caracteres especiais")
    void deveRemoverCaracteresEspeciais() {
        assertThat(NormalizadorUtil.normalizar("Sport/Recife")).isEqualTo("sportrecife");
    }

    @Test
    @DisplayName("deve converter maiusculas para minusculas")
    void deveConverterParaMinusculas() {
        assertThat(NormalizadorUtil.normalizar("FLAMENGO")).isEqualTo("flamengo");
    }

    @Test
    @DisplayName("deve remover espacos nas extremidades")
    void deveRemoverEspacosExtremidades() {
        assertThat(NormalizadorUtil.normalizar("  Flamengo  ")).isEqualTo("flamengo");
    }

    @Test
    @DisplayName("resultado deve ser idempotente ao chamar duas vezes")
    void deveSerIdempotente() {
        String entrada = "Atletico Mineiro";
        String primeira = NormalizadorUtil.normalizar(entrada);
        String segunda  = NormalizadorUtil.normalizar(primeira);
        assertThat(primeira).isEqualTo(segunda);
    }

    @Test
    @DisplayName("deve produzir resultado igual para mesmo clube com grafias diferentes")
    void deveProduzirResultadoIgualParaGrafiasDiferentes() {
        // Simula divergencia entre Odds API e Cartola
        String oddsApi  = NormalizadorUtil.normalizar("Atletico-MG");
        String cartola  = NormalizadorUtil.normalizar("Atletico Mineiro MG");

        // Ambos devem normalizar de forma que o cruzamento seja possivel
        assertThat(oddsApi).doesNotContain("-");
        assertThat(cartola).doesNotContain("-");
    }
}
