package com.cartola.odds.service;

import com.cartola.odds.model.ItemPagamento;
import com.cartola.odds.model.RelatorioPagamento;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Geracao do relatorio de pagamento em PDF utilizando OpenPDF.
 */
@Slf4j
@Service
public class ExportadorPdfService {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String[] COLUNAS = {
            "#", "Profissional", "Posicao", "Clube", "Funcao", "Status", "Pagamento (C$)"
    };

    private static final float[] LARGURAS = { 0.6f, 2.6f, 1.4f, 2.0f, 1.4f, 1.4f, 1.8f };

    public byte[] exportar(RelatorioPagamento relatorio) {
        if (relatorio == null) {
            throw new IllegalArgumentException("Relatorio nao pode ser nulo");
        }

        var output = new ByteArrayOutputStream();
        var documento = new Document(PageSize.A4, 36, 36, 48, 36);
        try {
            PdfWriter.getInstance(documento, output);
            documento.open();

            adicionarTitulo(documento, relatorio);
            adicionarMetadados(documento, relatorio);
            adicionarTabela(documento, relatorio);
            adicionarTotal(documento, relatorio);

        } catch (Exception ex) {
            log.error("Falha ao gerar PDF do relatorio de pagamento", ex);
            throw new IllegalStateException("Falha ao gerar PDF: " + ex.getMessage(), ex);
        } finally {
            if (documento.isOpen()) documento.close();
        }
        return output.toByteArray();
    }

    private void adicionarTitulo(Document doc, RelatorioPagamento r) {
        var fonte = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
        var titulo = new Paragraph("Relatorio de Pagamento dos Profissionais", fonte);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(8f);
        doc.add(titulo);

        var sub = new Paragraph("Rodada %d".formatted(r.getRodada()),
                FontFactory.getFont(FontFactory.HELVETICA, 11));
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(12f);
        doc.add(sub);
    }

    private void adicionarMetadados(Document doc, RelatorioPagamento r) {
        var fonte = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
        doc.add(new Paragraph("Gerado em: " + r.getGeradoEm().format(DATA_HORA), fonte));
        doc.add(new Paragraph("Total de profissionais: " + r.getTotalProfissionais(), fonte));
        if (r.getAvisoMercado() != null && !r.getAvisoMercado().isBlank()) {
            var aviso = new Paragraph("Aviso de mercado: " + r.getAvisoMercado(),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.RED));
            aviso.setSpacingBefore(4f);
            doc.add(aviso);
        }
        doc.add(new Paragraph(" "));
    }

    private void adicionarTabela(Document doc, RelatorioPagamento r) throws Exception {
        var tabela = new PdfPTable(COLUNAS.length);
        tabela.setWidthPercentage(100);
        tabela.setWidths(LARGURAS);
        tabela.setSpacingBefore(4f);
        tabela.setHeaderRows(1);

        var fonteHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        for (String coluna : COLUNAS) {
            var cell = new PdfPCell(new Phrase(coluna, fonteHeader));
            cell.setBackgroundColor(new Color(33, 64, 95));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(6f);
            tabela.addCell(cell);
        }

        var fonteCorpo = FontFactory.getFont(FontFactory.HELVETICA, 10);
        int rank = 1;
        for (ItemPagamento item : r.getItens()) {
            tabela.addCell(textoCentralizado(String.valueOf(rank++), fonteCorpo));
            tabela.addCell(texto(safe(item.getApelido()), fonteCorpo));
            tabela.addCell(textoCentralizado(item.getPosicaoLabel(), fonteCorpo));
            tabela.addCell(texto(safe(item.getNomeClube()), fonteCorpo));
            tabela.addCell(textoCentralizado(item.getFuncao(), fonteCorpo));
            tabela.addCell(textoCentralizado(item.getStatusLabel(), fonteCorpo));
            tabela.addCell(textoDireita(formatarMoeda(item.getValorPagamento()), fonteCorpo));
        }
        doc.add(tabela);
    }

    private void adicionarTotal(Document doc, RelatorioPagamento r) {
        var fonte = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        var paragrafo = new Paragraph(
                "Total geral: %s %s".formatted(r.getMoeda(), formatarMoeda(r.getTotalPagamento())),
                fonte);
        paragrafo.setAlignment(Element.ALIGN_RIGHT);
        paragrafo.setSpacingBefore(12f);
        doc.add(paragrafo);
    }

    private PdfPCell texto(String valor, Font fonte) {
        var cell = new PdfPCell(new Phrase(valor, fonte));
        cell.setPadding(5f);
        return cell;
    }

    private PdfPCell textoCentralizado(String valor, Font fonte) {
        var cell = texto(valor, fonte);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private PdfPCell textoDireita(String valor, Font fonte) {
        var cell = texto(valor, fonte);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private String safe(String valor) {
        return valor == null ? "" : valor;
    }

    private String formatarMoeda(double valor) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", valor);
    }
}
