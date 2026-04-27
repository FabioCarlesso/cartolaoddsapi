package com.cartola.odds.controller;

import com.cartola.odds.controller.api.RelatorioPagamentoApi;
import com.cartola.odds.model.RelatorioPagamento;
import com.cartola.odds.model.enums.FormatoExportacao;
import com.cartola.odds.model.response.ExportacaoResponse;
import com.cartola.odds.model.response.RelatorioPagamentoResponse;
import com.cartola.odds.service.ExportadorExcelService;
import com.cartola.odds.service.ExportadorPdfService;
import com.cartola.odds.service.RelatorioPagamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RelatorioPagamentoController implements RelatorioPagamentoApi {

    private final RelatorioPagamentoService relatorioService;
    private final ExportadorPdfService      exportadorPdf;
    private final ExportadorExcelService    exportadorExcel;

    @Override
    public ResponseEntity<RelatorioPagamentoResponse> obter() {
        log.info("GET /api/relatorio/pagamento");
        var relatorio = relatorioService.gerar();
        return ResponseEntity.ok(RelatorioPagamentoResponse.from(relatorio));
    }

    @Override
    public ResponseEntity<ExportacaoResponse> exportar(String formato) {
        log.info("GET /api/relatorio/pagamento/exportar | formato={}", formato);
        FormatoExportacao formatoEnum = parseFormato(formato);
        var relatorio = relatorioService.gerar();
        byte[] conteudo = gerar(formatoEnum, relatorio);
        String fileName = montarNomeArquivo(relatorio.getRodada(), formatoEnum);
        return ResponseEntity.ok(ExportacaoResponse.of(conteudo, fileName, formatoEnum));
    }

    @Override
    public ResponseEntity<Resource> download(String formato) {
        log.info("GET /api/relatorio/pagamento/download | formato={}", formato);
        FormatoExportacao formatoEnum = parseFormato(formato);
        var relatorio = relatorioService.gerar();
        byte[] conteudo = gerar(formatoEnum, relatorio);
        String fileName = montarNomeArquivo(relatorio.getRodada(), formatoEnum);

        var resource = new ByteArrayResource(conteudo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(formatoEnum.getMimeType()))
                .contentLength(conteudo.length)
                .body(resource);
    }

    private byte[] gerar(FormatoExportacao formato, RelatorioPagamento relatorio) {
        return switch (formato) {
            case PDF   -> exportadorPdf.exportar(relatorio);
            case EXCEL -> exportadorExcel.exportar(relatorio);
        };
    }

    private FormatoExportacao parseFormato(String formato) {
        return FormatoExportacao.fromString(formato)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Formato invalido: '%s'. Valores aceitos: PDF, EXCEL".formatted(formato)));
    }

    private String montarNomeArquivo(int rodada, FormatoExportacao formato) {
        return "relatorio-pagamento-rodada-%d.%s".formatted(rodada, formato.getExtensao());
    }
}
