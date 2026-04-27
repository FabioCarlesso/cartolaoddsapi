package com.cartola.odds.service;

import com.cartola.odds.model.ItemPagamento;
import com.cartola.odds.model.RelatorioPagamento;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExportadorPdfService")
class ExportadorPdfServiceTest {

    private final ExportadorPdfService service = new ExportadorPdfService();

    @Test
    @DisplayName("deve gerar PDF valido com cabecalho %PDF-")
    void deveGerarPdfValido() {
        byte[] pdf = service.exportar(relatorioCompleto());

        assertThat(pdf).isNotNull().isNotEmpty();
        assertThat(pdf.length).isGreaterThan(500);
        var prefixo = new String(pdf, 0, 5);
        assertThat(prefixo).startsWith("%PDF-");
    }

    @Test
    @DisplayName("deve gerar PDF com aviso de mercado quando presente")
    void deveGerarPdfComAvisoMercado() {
        var relatorio = RelatorioPagamento.builder()
                .rodada(15)
                .avisoMercado("Mercado fechado. Rodada em andamento.")
                .geradoEm(LocalDateTime.now())
                .moeda("C$")
                .totalProfissionais(1)
                .totalPagamento(22.0)
                .itens(List.of(itemMock("Hulk", "ATM", "Atletico Mineiro", 22.0, true, true)))
                .build();

        byte[] pdf = service.exportar(relatorio);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("deve gerar PDF mesmo sem itens")
    void deveGerarPdfSemItens() {
        var relatorio = RelatorioPagamento.builder()
                .rodada(1).avisoMercado(null).geradoEm(LocalDateTime.now())
                .moeda("C$").totalProfissionais(0).totalPagamento(0.0)
                .itens(List.of()).build();

        byte[] pdf = service.exportar(relatorio);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).startsWith("%PDF-");
    }

    @Test
    @DisplayName("deve lancar IllegalArgumentException quando relatorio for nulo")
    void deveLancarQuandoNulo() {
        assertThatThrownBy(() -> service.exportar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RelatorioPagamento relatorioCompleto() {
        return RelatorioPagamento.builder()
                .rodada(15).avisoMercado(null).geradoEm(LocalDateTime.now())
                .moeda("C$").totalProfissionais(2).totalPagamento(40.3)
                .itens(List.of(
                        itemMock("Hulk", "ATM", "Atletico Mineiro", 22.0, true, true),
                        itemMock("Cano", "FLU", "Fluminense",       18.3, true, false)))
                .build();
    }

    private ItemPagamento itemMock(String apelido, String sigla, String nomeClube,
                                   double preco, boolean titular, boolean capitao) {
        return ItemPagamento.builder()
                .atletaId(1).apelido(apelido).posicao(Posicao.ATA)
                .siglaClube(sigla).nomeClube(nomeClube).status(StatusAtleta.PROVAVEL)
                .valorPagamento(preco).titular(titular).capitao(capitao).build();
    }
}
