package com.cartola.odds.controller;

import com.cartola.odds.model.ItemPagamento;
import com.cartola.odds.model.RelatorioPagamento;
import com.cartola.odds.model.enums.Posicao;
import com.cartola.odds.model.enums.StatusAtleta;
import com.cartola.odds.service.ExportadorExcelService;
import com.cartola.odds.service.ExportadorPdfService;
import com.cartola.odds.service.RelatorioPagamentoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RelatorioPagamentoController.class)
@DisplayName("RelatorioPagamentoController")
class RelatorioPagamentoControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RelatorioPagamentoService relatorioService;
    @MockitoBean ExportadorPdfService      exportadorPdf;
    @MockitoBean ExportadorExcelService    exportadorExcel;

    @Nested
    @DisplayName("GET /api/relatorio/pagamento")
    class Obter {

        @Test
        @DisplayName("deve retornar 200 com contrato amigavel ao Angular")
        void deveRetornar200ComContratoCompleto() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());

            mockMvc.perform(get("/api/relatorio/pagamento"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.rodada").value(15))
                    .andExpect(jsonPath("$.moeda").value("C$"))
                    .andExpect(jsonPath("$.totalProfissionais").value(2))
                    .andExpect(jsonPath("$.totalPagamento").value(40.3))
                    .andExpect(jsonPath("$.geradoEm").exists())
                    .andExpect(jsonPath("$.itens").isArray())
                    .andExpect(jsonPath("$.itens[0].apelido").value("Hulk"))
                    .andExpect(jsonPath("$.itens[0].funcao").value("Capitao"))
                    .andExpect(jsonPath("$.itens[0].posicaoLabel").value("Atacante"))
                    .andExpect(jsonPath("$.itens[0].valorPagamento").value(22.0));
        }

        @Test
        @DisplayName("deve retornar 422 quando pipeline lancar IllegalStateException")
        void deveRetornar422QuandoPipelineFalhar() throws Exception {
            when(relatorioService.gerar())
                    .thenThrow(new IllegalStateException("Nenhum atleta disponivel"));

            mockMvc.perform(get("/api/relatorio/pagamento"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value(422))
                    .andExpect(jsonPath("$.mensagem").value(containsString("Nenhum atleta")));
        }

        @Test
        @DisplayName("deve omitir avisoMercado quando mercado aberto")
        void deveOmitirAvisoQuandoMercadoAberto() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());
            mockMvc.perform(get("/api/relatorio/pagamento"))
                    .andExpect(jsonPath("$.avisoMercado").doesNotExist());
        }

        @Test
        @DisplayName("deve preencher avisoMercado quando presente")
        void devePreencherAvisoMercado() throws Exception {
            var relatorio = RelatorioPagamento.builder()
                    .rodada(15).avisoMercado("Mercado fechado.")
                    .geradoEm(LocalDateTime.now()).moeda("C$")
                    .totalProfissionais(0).totalPagamento(0.0)
                    .itens(List.of()).build();
            when(relatorioService.gerar()).thenReturn(relatorio);

            mockMvc.perform(get("/api/relatorio/pagamento"))
                    .andExpect(jsonPath("$.avisoMercado").value("Mercado fechado."));
        }
    }

    @Nested
    @DisplayName("GET /api/relatorio/pagamento/exportar")
    class Exportar {

        @Test
        @DisplayName("deve exportar PDF com base64 e metadados")
        void deveExportarPdfBase64() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());
            when(exportadorPdf.exportar(any())).thenReturn(new byte[]{ 1, 2, 3, 4 });

            mockMvc.perform(get("/api/relatorio/pagamento/exportar?formato=PDF"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileName").value("relatorio-pagamento-rodada-15.pdf"))
                    .andExpect(jsonPath("$.mimeType").value("application/pdf"))
                    .andExpect(jsonPath("$.extensao").value("pdf"))
                    .andExpect(jsonPath("$.formato").value("PDF"))
                    .andExpect(jsonPath("$.sizeBytes").value(4))
                    .andExpect(jsonPath("$.conteudoBase64").value("AQIDBA=="));
        }

        @Test
        @DisplayName("deve exportar Excel com base64 e metadados")
        void deveExportarExcelBase64() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());
            when(exportadorExcel.exportar(any())).thenReturn(new byte[]{ 5, 6, 7 });

            mockMvc.perform(get("/api/relatorio/pagamento/exportar?formato=EXCEL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileName").value("relatorio-pagamento-rodada-15.xlsx"))
                    .andExpect(jsonPath("$.mimeType").value(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(jsonPath("$.formato").value("EXCEL"))
                    .andExpect(jsonPath("$.sizeBytes").value(3));
        }

        @Test
        @DisplayName("deve assumir PDF quando formato omitido")
        void deveAssumirPdfQuandoOmitido() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());
            when(exportadorPdf.exportar(any())).thenReturn(new byte[]{ 1 });

            mockMvc.perform(get("/api/relatorio/pagamento/exportar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.formato").value("PDF"));
        }

        @Test
        @DisplayName("deve retornar 400 para formato invalido")
        void deveRetornar400ParaFormatoInvalido() throws Exception {
            mockMvc.perform(get("/api/relatorio/pagamento/exportar?formato=DOC"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.mensagem").value(containsString("Formato invalido")));
        }
    }

    @Nested
    @DisplayName("GET /api/relatorio/pagamento/download")
    class Download {

        @Test
        @DisplayName("deve baixar PDF binario com Content-Disposition")
        void deveBaixarPdf() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());
            when(exportadorPdf.exportar(any())).thenReturn("%PDF-1.4 fake".getBytes());

            mockMvc.perform(get("/api/relatorio/pagamento/download?formato=PDF"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition",
                            containsString("relatorio-pagamento-rodada-15.pdf")))
                    .andExpect(header().longValue("Content-Length",
                            "%PDF-1.4 fake".getBytes().length));
        }

        @Test
        @DisplayName("deve baixar XLSX binario")
        void deveBaixarExcel() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());
            when(exportadorExcel.exportar(any())).thenReturn(new byte[]{ 1, 2, 3, 4, 5 });

            mockMvc.perform(get("/api/relatorio/pagamento/download?formato=EXCEL"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string("Content-Disposition",
                            containsString("relatorio-pagamento-rodada-15.xlsx")))
                    .andExpect(content().bytes(new byte[]{ 1, 2, 3, 4, 5 }));
        }

        @Test
        @DisplayName("deve retornar 400 para formato invalido")
        void deveRetornar400ParaFormatoInvalido() throws Exception {
            mockMvc.perform(get("/api/relatorio/pagamento/download?formato=zip"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(containsString("Formato invalido")));
        }

        @Test
        @DisplayName("deve aceitar formato em minusculo")
        void deveAceitarMinusculo() throws Exception {
            when(relatorioService.gerar()).thenReturn(relatorioMock());
            when(exportadorPdf.exportar(any())).thenReturn(new byte[]{ 9 });

            mockMvc.perform(get("/api/relatorio/pagamento/download?formato=pdf"))
                    .andExpect(status().isOk())
                    .andExpect(header().longValue("Content-Length", 1));
        }
    }

    private RelatorioPagamento relatorioMock() {
        return RelatorioPagamento.builder()
                .rodada(15).avisoMercado(null)
                .geradoEm(LocalDateTime.of(2025, 4, 27, 15, 30))
                .moeda("C$").totalProfissionais(2).totalPagamento(40.3)
                .itens(List.of(
                        ItemPagamento.builder()
                                .atletaId(1).apelido("Hulk").posicao(Posicao.ATA)
                                .siglaClube("ATM").nomeClube("Atletico Mineiro")
                                .status(StatusAtleta.PROVAVEL)
                                .valorPagamento(22.0).titular(true).capitao(true).build(),
                        ItemPagamento.builder()
                                .atletaId(2).apelido("Cano").posicao(Posicao.ATA)
                                .siglaClube("FLU").nomeClube("Fluminense")
                                .status(StatusAtleta.PROVAVEL)
                                .valorPagamento(18.3).titular(true).capitao(false).build()))
                .build();
    }
}
