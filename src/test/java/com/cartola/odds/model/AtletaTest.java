package com.cartola.odds.model;

import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Atleta")
class AtletaTest {

    @Test
    @DisplayName("formatado deve retornar 'Apelido (SIG)' para atleta provavel")
    void formatadoDeveRetornarFormatoBasico() {
        var atleta = base().build();
        assertThat(atleta.formatado()).isEqualTo("Hulk (ATM)");
    }

    @Test
    @DisplayName("formatado deve incluir alerta de duvida para atleta em duvida")
    void formatadoDeveIncluirAlertaDeDuvida() {
        var atleta = base().status(StatusAtleta.DUVIDA).build();
        assertThat(atleta.formatado()).contains("DÚVIDA");
        assertThat(atleta.formatado()).startsWith("Hulk (ATM)");
    }

    @Test
    @DisplayName("isDuvida deve ser true somente para status DUVIDA")
    void isDuvidaDeveReconhecerStatusDuvida() {
        assertThat(base().status(StatusAtleta.DUVIDA).build().isDuvida()).isTrue();
        assertThat(base().status(StatusAtleta.PROVAVEL).build().isDuvida()).isFalse();
        assertThat(base().status(StatusAtleta.CONTUNDIDO).build().isDuvida()).isFalse();
    }

    @Test
    @DisplayName("isProvavel deve ser true somente para status PROVAVEL")
    void isProvavelDeveReconhecerStatusProvavel() {
        assertThat(base().status(StatusAtleta.PROVAVEL).build().isProvavel()).isTrue();
        assertThat(base().status(StatusAtleta.DUVIDA).build().isProvavel()).isFalse();
    }

    @Test
    @DisplayName("withScore deve criar nova instancia sem modificar a original")
    void withScoreDevePreservarImutabilidade() {
        var original = base().score(0.0).build();
        var comScore = original.withScore(9.9);

        assertThat(original.getScore()).isEqualTo(0.0);
        assertThat(comScore.getScore()).isEqualTo(9.9);
        assertThat(comScore.getApelido()).isEqualTo(original.getApelido());
    }

    @Test
    @DisplayName("withStatus deve criar nova instancia sem modificar a original")
    void withStatusDevePreservarImutabilidade() {
        var original = base().status(StatusAtleta.PROVAVEL).build();
        var emDuvida = original.withStatus(StatusAtleta.DUVIDA);

        assertThat(original.getStatus()).isEqualTo(StatusAtleta.PROVAVEL);
        assertThat(emDuvida.getStatus()).isEqualTo(StatusAtleta.DUVIDA);
    }

    @Test
    @DisplayName("withDesvioPadrao deve criar nova instancia sem modificar a original")
    void withDesvioPadraoDevePreservarImutabilidade() {
        var original = base().desvioPadrao(0.0).build();
        var comDesvio = original.withDesvioPadrao(2.5);

        assertThat(original.getDesvioPadrao()).isEqualTo(0.0);
        assertThat(comDesvio.getDesvioPadrao()).isEqualTo(2.5);
        assertThat(comDesvio.getApelido()).isEqualTo(original.getApelido());
    }

    @Test
    @DisplayName("withRodadasConsideradas deve criar nova instancia sem modificar a original")
    void withRodadasConsideradasDevePreservarImutabilidade() {
        var original = base().rodadasConsideradas(0).build();
        var comRodadas = original.withRodadasConsideradas(5);

        assertThat(original.getRodadasConsideradas()).isEqualTo(0);
        assertThat(comRodadas.getRodadasConsideradas()).isEqualTo(5);
        assertThat(comRodadas.getApelido()).isEqualTo(original.getApelido());
    }

    @Test
    @DisplayName("withSubstitutoProvavel deve injetar substituto sem modificar o original")
    void withSubstitutoDevePreservarImutabilidade() {
        var titular = base().build();
        var sub     = base().apelido("Cano").build();

        var comSub = titular.withSubstitutoProvavel(sub);

        assertThat(titular.getSubstitutoProvavel()).isNull();
        assertThat(comSub.getSubstitutoProvavel().getApelido()).isEqualTo("Cano");
    }

    // ── Helper ──────────────────────────────────────────────────────

    private Atleta.AtletaBuilder base() {
        return Atleta.builder()
                .atletaId(1)
                .apelido("Hulk")
                .posicao(Posicao.ATA)
                .clubeId(1)
                .nomeClube("Atletico Mineiro")
                .siglaClube("ATM")
                .nomeClubeNorm("atletico mineiro")
                .status(StatusAtleta.PROVAVEL)
                .mediaPontos(9.5)
                .valorizacao(2.0)
                .preco(22.0)
                .desempenhoRecente(0.0)
                .desvioPadrao(0.0)
                .rodadasConsideradas(0)
                .score(0.0);
    }
}
