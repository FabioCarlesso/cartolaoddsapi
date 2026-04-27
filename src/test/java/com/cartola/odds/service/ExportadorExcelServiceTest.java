package com.cartola.odds.service;

import com.cartola.odds.model.ItemPagamento;
import com.cartola.odds.model.RelatorioPagamento;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExportadorExcelService")
class ExportadorExcelServiceTest {

    private final ExportadorExcelService service = new ExportadorExcelService();

    @Test
    @DisplayName("deve gerar XLSX valido com aba 'Pagamentos'")
    void deveGerarXlsxValido() throws Exception {
        byte[] xlsx = service.exportar(relatorioCompleto());

        assertThat(xlsx).isNotNull().isNotEmpty();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Pagamentos");
        }
    }

    @Test
    @DisplayName("deve incluir cabecalho com colunas esperadas")
    void deveIncluirCabecalho() throws Exception {
        byte[] xlsx = service.exportar(relatorioCompleto());

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            int linhaHeader = encontrarLinhaHeader(sheet);
            var header = sheet.getRow(linhaHeader);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("#");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Profissional");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Posicao");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Clube");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Funcao");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Status");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Pagamento (C$)");
        }
    }

    @Test
    @DisplayName("deve incluir os itens na planilha")
    void deveIncluirItens() throws Exception {
        byte[] xlsx = service.exportar(relatorioCompleto());

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            int linhaHeader = encontrarLinhaHeader(sheet);
            var primeiroItem = sheet.getRow(linhaHeader + 1);
            assertThat(primeiroItem.getCell(1).getStringCellValue()).isEqualTo("Hulk");
            assertThat(primeiroItem.getCell(6).getNumericCellValue()).isEqualTo(22.0);
        }
    }

    @Test
    @DisplayName("deve incluir total na ultima linha")
    void deveIncluirTotal() throws Exception {
        var relatorio = relatorioCompleto();
        byte[] xlsx = service.exportar(relatorio);

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            var ultima = sheet.getRow(sheet.getLastRowNum());
            assertThat(ultima.getCell(6).getNumericCellValue())
                    .isEqualTo(relatorio.getTotalPagamento());
        }
    }

    @Test
    @DisplayName("deve gerar planilha mesmo sem itens")
    void deveGerarSemItens() throws Exception {
        var relatorio = RelatorioPagamento.builder()
                .rodada(1).avisoMercado(null).geradoEm(LocalDateTime.now())
                .moeda("C$").totalProfissionais(0).totalPagamento(0.0)
                .itens(List.of()).build();

        byte[] xlsx = service.exportar(relatorio);
        assertThat(xlsx).isNotEmpty();

        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Pagamentos");
        }
    }

    @Test
    @DisplayName("deve incluir aviso de mercado quando presente")
    void deveIncluirAvisoMercado() throws Exception {
        var relatorio = RelatorioPagamento.builder()
                .rodada(15).avisoMercado("Mercado fechado. Rodada em andamento.")
                .geradoEm(LocalDateTime.now()).moeda("C$")
                .totalProfissionais(1).totalPagamento(22.0)
                .itens(List.of(itemMock("Hulk", 22.0, true, true)))
                .build();

        byte[] xlsx = service.exportar(relatorio);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);
            boolean encontrou = false;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                var row = sheet.getRow(i);
                if (row != null && row.getCell(0) != null
                    && row.getCell(0).getStringCellValue().contains("Aviso de mercado")) {
                    encontrou = true;
                    break;
                }
            }
            assertThat(encontrou).isTrue();
        }
    }

    @Test
    @DisplayName("deve lancar IllegalArgumentException quando relatorio for nulo")
    void deveLancarQuandoNulo() {
        assertThatThrownBy(() -> service.exportar(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private int encontrarLinhaHeader(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            var row = sheet.getRow(i);
            if (row != null && row.getCell(0) != null
                && "#".equals(row.getCell(0).getStringCellValue())) {
                return i;
            }
        }
        throw new AssertionError("Linha de header nao encontrada");
    }

    private RelatorioPagamento relatorioCompleto() {
        return RelatorioPagamento.builder()
                .rodada(15).avisoMercado(null).geradoEm(LocalDateTime.now())
                .moeda("C$").totalProfissionais(2).totalPagamento(40.3)
                .itens(List.of(
                        itemMock("Hulk", 22.0, true, true),
                        itemMock("Cano", 18.3, true, false)))
                .build();
    }

    private ItemPagamento itemMock(String apelido, double preco, boolean titular, boolean capitao) {
        return ItemPagamento.builder()
                .atletaId(1).apelido(apelido).posicao(Posicao.ATA)
                .siglaClube("CLB").nomeClube("Clube").status(StatusAtleta.PROVAVEL)
                .valorPagamento(preco).titular(titular).capitao(capitao).build();
    }
}
