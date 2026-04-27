package com.cartola.odds.model;

import com.cartola.odds.model.enums.FormatoExportacao;
import com.cartola.odds.model.response.ExportacaoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExportacaoResponse")
class ExportacaoResponseTest {

    @Test
    @DisplayName("of deve preencher metadados e codificar conteudo em Base64")
    void devePreencherMetadados() {
        byte[] bytes = { 1, 2, 3, 4, 5 };
        var resp = ExportacaoResponse.of(bytes, "arquivo.pdf", FormatoExportacao.PDF);

        assertThat(resp.getFileName()).isEqualTo("arquivo.pdf");
        assertThat(resp.getMimeType()).isEqualTo("application/pdf");
        assertThat(resp.getExtensao()).isEqualTo("pdf");
        assertThat(resp.getFormato()).isEqualTo("PDF");
        assertThat(resp.getSizeBytes()).isEqualTo(5);
        assertThat(resp.getGeradoEm()).isNotNull();
        assertThat(Base64.getDecoder().decode(resp.getConteudoBase64())).isEqualTo(bytes);
    }

    @Test
    @DisplayName("of deve usar metadados do formato Excel")
    void deveUsarFormatoExcel() {
        var resp = ExportacaoResponse.of(new byte[]{ 9, 9 }, "p.xlsx", FormatoExportacao.EXCEL);

        assertThat(resp.getMimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(resp.getExtensao()).isEqualTo("xlsx");
        assertThat(resp.getFormato()).isEqualTo("EXCEL");
    }

    @Test
    @DisplayName("of deve aceitar conteudo vazio")
    void deveAceitarVazio() {
        var resp = ExportacaoResponse.of(new byte[0], "vazio.pdf", FormatoExportacao.PDF);
        assertThat(resp.getSizeBytes()).isZero();
        assertThat(resp.getConteudoBase64()).isEmpty();
    }
}
