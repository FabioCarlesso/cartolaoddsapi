package com.cartola.odds.service;

import com.cartola.odds.model.ItemPagamento;
import com.cartola.odds.model.RelatorioPagamento;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Geracao do relatorio de pagamento em planilha Excel (XLSX) via Apache POI.
 */
@Slf4j
@Service
public class ExportadorExcelService {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] COLUNAS = {
            "#", "Profissional", "Posicao", "Clube", "Funcao", "Status", "Pagamento (C$)"
    };

    public byte[] exportar(RelatorioPagamento relatorio) {
        if (relatorio == null) {
            throw new IllegalArgumentException("Relatorio nao pode ser nulo");
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Pagamentos");
            CellStyle estiloTitulo   = estiloTitulo(workbook);
            CellStyle estiloMeta     = estiloMeta(workbook);
            CellStyle estiloHeader   = estiloHeader(workbook);
            CellStyle estiloTexto    = estiloCorpo(workbook, HorizontalAlignment.LEFT);
            CellStyle estiloCentral  = estiloCorpo(workbook, HorizontalAlignment.CENTER);
            CellStyle estiloMoeda    = estiloMoeda(workbook);
            CellStyle estiloTotal    = estiloTotal(workbook);
            CellStyle estiloTotalNum = estiloTotalNumero(workbook);

            int linhaIdx = 0;
            linhaIdx = escreverTitulo(sheet, linhaIdx, relatorio, estiloTitulo);
            linhaIdx = escreverMetadados(sheet, linhaIdx, relatorio, estiloMeta);
            linhaIdx++;
            linhaIdx = escreverHeader(sheet, linhaIdx, estiloHeader);
            linhaIdx = escreverItens(sheet, linhaIdx, relatorio, estiloTexto, estiloCentral, estiloMoeda);
            escreverTotal(sheet, linhaIdx, relatorio, estiloTotal, estiloTotalNum);

            for (int c = 0; c < COLUNAS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            log.error("Falha ao gerar Excel do relatorio de pagamento", ex);
            throw new IllegalStateException("Falha ao gerar Excel: " + ex.getMessage(), ex);
        }
    }

    private int escreverTitulo(Sheet sheet, int linhaIdx, RelatorioPagamento r, CellStyle estilo) {
        Row linha = sheet.createRow(linhaIdx);
        Cell celula = linha.createCell(0);
        celula.setCellValue("Relatorio de Pagamento dos Profissionais — Rodada " + r.getRodada());
        celula.setCellStyle(estilo);
        sheet.addMergedRegion(new CellRangeAddress(linhaIdx, linhaIdx, 0, COLUNAS.length - 1));
        return linhaIdx + 1;
    }

    private int escreverMetadados(Sheet sheet, int linhaIdx, RelatorioPagamento r, CellStyle estilo) {
        Row geradoEm = sheet.createRow(linhaIdx++);
        Cell c1 = geradoEm.createCell(0);
        c1.setCellValue("Gerado em: " + r.getGeradoEm().format(DATA_HORA));
        c1.setCellStyle(estilo);

        Row totalProf = sheet.createRow(linhaIdx++);
        Cell c2 = totalProf.createCell(0);
        c2.setCellValue("Total de profissionais: " + r.getTotalProfissionais());
        c2.setCellStyle(estilo);

        if (r.getAvisoMercado() != null && !r.getAvisoMercado().isBlank()) {
            Row aviso = sheet.createRow(linhaIdx++);
            Cell c3 = aviso.createCell(0);
            c3.setCellValue("Aviso de mercado: " + r.getAvisoMercado());
            c3.setCellStyle(estilo);
        }
        return linhaIdx;
    }

    private int escreverHeader(Sheet sheet, int linhaIdx, CellStyle estilo) {
        Row header = sheet.createRow(linhaIdx);
        for (int c = 0; c < COLUNAS.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellValue(COLUNAS[c]);
            cell.setCellStyle(estilo);
        }
        return linhaIdx + 1;
    }

    private int escreverItens(Sheet sheet, int linhaIdx, RelatorioPagamento r,
                              CellStyle texto, CellStyle central, CellStyle moeda) {
        int rank = 1;
        for (ItemPagamento item : r.getItens()) {
            Row linha = sheet.createRow(linhaIdx++);

            Cell cRank = linha.createCell(0);
            cRank.setCellValue(rank++);
            cRank.setCellStyle(central);

            Cell cNome = linha.createCell(1);
            cNome.setCellValue(safe(item.getApelido()));
            cNome.setCellStyle(texto);

            Cell cPos = linha.createCell(2);
            cPos.setCellValue(item.getPosicaoLabel());
            cPos.setCellStyle(central);

            Cell cClube = linha.createCell(3);
            cClube.setCellValue(safe(item.getNomeClube()));
            cClube.setCellStyle(texto);

            Cell cFunc = linha.createCell(4);
            cFunc.setCellValue(item.getFuncao());
            cFunc.setCellStyle(central);

            Cell cStatus = linha.createCell(5);
            cStatus.setCellValue(item.getStatusLabel());
            cStatus.setCellStyle(central);

            Cell cValor = linha.createCell(6);
            cValor.setCellValue(item.getValorPagamento());
            cValor.setCellStyle(moeda);
        }
        return linhaIdx;
    }

    private void escreverTotal(Sheet sheet, int linhaIdx, RelatorioPagamento r,
                               CellStyle estilo, CellStyle estiloNumero) {
        Row total = sheet.createRow(linhaIdx);
        Cell label = total.createCell(0);
        label.setCellValue("Total geral (" + r.getMoeda() + ")");
        label.setCellStyle(estilo);
        sheet.addMergedRegion(new CellRangeAddress(linhaIdx, linhaIdx, 0, 5));

        Cell valor = total.createCell(6);
        valor.setCellValue(r.getTotalPagamento());
        valor.setCellStyle(estiloNumero);
    }

    private CellStyle estiloTitulo(Workbook wb) {
        Font fonte = wb.createFont();
        fonte.setBold(true);
        fonte.setFontHeightInPoints((short) 14);
        CellStyle style = wb.createCellStyle();
        style.setFont(fonte);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle estiloMeta(Workbook wb) {
        Font fonte = wb.createFont();
        fonte.setItalic(true);
        fonte.setFontHeightInPoints((short) 10);
        CellStyle style = wb.createCellStyle();
        style.setFont(fonte);
        return style;
    }

    private CellStyle estiloHeader(Workbook wb) {
        Font fonte = wb.createFont();
        fonte.setBold(true);
        fonte.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
        CellStyle style = wb.createCellStyle();
        style.setFont(fonte);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        if (style instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle xssf) {
            xssf.setFillForegroundColor(new XSSFColor(new Color(33, 64, 95), null));
        } else {
            style.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());
        }
        aplicarBordas(style);
        return style;
    }

    private CellStyle estiloCorpo(Workbook wb, HorizontalAlignment alinhamento) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(alinhamento);
        aplicarBordas(style);
        return style;
    }

    private CellStyle estiloMoeda(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        aplicarBordas(style);
        return style;
    }

    private CellStyle estiloTotal(Workbook wb) {
        Font fonte = wb.createFont();
        fonte.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(fonte);
        style.setAlignment(HorizontalAlignment.RIGHT);
        aplicarBordas(style);
        return style;
    }

    private CellStyle estiloTotalNumero(Workbook wb) {
        Font fonte = wb.createFont();
        fonte.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(fonte);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        aplicarBordas(style);
        return style;
    }

    private void aplicarBordas(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String safe(String valor) {
        return valor == null ? "" : valor;
    }
}
