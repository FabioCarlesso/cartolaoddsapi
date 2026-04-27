package com.cartola.odds.model;

import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ItemPagamento")
class ItemPagamentoTest {

    @Test
    @DisplayName("getFuncao deve retornar 'Capitao' quando capitao=true")
    void funcaoCapitao() {
        var item = ItemPagamento.builder().capitao(true).titular(true).build();
        assertThat(item.getFuncao()).isEqualTo("Capitao");
    }

    @Test
    @DisplayName("getFuncao deve retornar 'Titular' quando titular=true e capitao=false")
    void funcaoTitular() {
        var item = ItemPagamento.builder().titular(true).capitao(false).build();
        assertThat(item.getFuncao()).isEqualTo("Titular");
    }

    @Test
    @DisplayName("getFuncao deve retornar 'Reserva' quando titular=false")
    void funcaoReserva() {
        var item = ItemPagamento.builder().titular(false).capitao(false).build();
        assertThat(item.getFuncao()).isEqualTo("Reserva");
    }

    @Test
    @DisplayName("getPosicaoLabel deve retornar label da posicao")
    void posicaoLabelPreenchida() {
        var item = ItemPagamento.builder().posicao(Posicao.ATA).build();
        assertThat(item.getPosicaoLabel()).isEqualTo("Atacante");
    }

    @Test
    @DisplayName("getPosicaoLabel deve retornar string vazia quando posicao for null")
    void posicaoLabelNula() {
        var item = ItemPagamento.builder().posicao(null).build();
        assertThat(item.getPosicaoLabel()).isEmpty();
    }

    @Test
    @DisplayName("getStatusLabel deve retornar label do status")
    void statusLabelPreenchido() {
        var item = ItemPagamento.builder().status(StatusAtleta.PROVAVEL).build();
        assertThat(item.getStatusLabel()).isEqualTo("Provável");
    }

    @Test
    @DisplayName("getStatusLabel deve retornar string vazia quando status for null")
    void statusLabelNulo() {
        var item = ItemPagamento.builder().status(null).build();
        assertThat(item.getStatusLabel()).isEmpty();
    }
}
