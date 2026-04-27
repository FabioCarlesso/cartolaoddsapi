package com.cartola.odds.model;

import com.cartola.odds.model.enums.FormatoExportacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FormatoExportacao")
class FormatoExportacaoTest {

    @Test
    @DisplayName("PDF deve ter extensao 'pdf' e mime application/pdf")
    void pdfMetadados() {
        assertThat(FormatoExportacao.PDF.getExtensao()).isEqualTo("pdf");
        assertThat(FormatoExportacao.PDF.getMimeType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("EXCEL deve ter extensao 'xlsx' e mime do Office Open XML")
    void excelMetadados() {
        assertThat(FormatoExportacao.EXCEL.getExtensao()).isEqualTo("xlsx");
        assertThat(FormatoExportacao.EXCEL.getMimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    @DisplayName("fromString deve aceitar valores em minusculas")
    void fromStringMinusculas() {
        assertThat(FormatoExportacao.fromString("pdf")).contains(FormatoExportacao.PDF);
        assertThat(FormatoExportacao.fromString("excel")).contains(FormatoExportacao.EXCEL);
    }

    @Test
    @DisplayName("fromString deve aceitar valores com espacos")
    void fromStringComEspacos() {
        assertThat(FormatoExportacao.fromString("  PDF  ")).contains(FormatoExportacao.PDF);
    }

    @Test
    @DisplayName("fromString deve retornar empty para valores invalidos ou nulos")
    void fromStringInvalido() {
        assertThat(FormatoExportacao.fromString(null)).isEmpty();
        assertThat(FormatoExportacao.fromString("")).isEmpty();
        assertThat(FormatoExportacao.fromString("   ")).isEmpty();
        assertThat(FormatoExportacao.fromString("DOC")).isEmpty();
    }
}
