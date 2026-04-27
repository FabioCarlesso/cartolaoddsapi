package com.cartola.odds.model.response;

import com.cartola.odds.model.enums.FormatoExportacao;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Wrapper de exportacao binaria amigavel ao Angular.
 *
 * <p>Inclui metadados (nome, mime type, tamanho) e o conteudo do arquivo
 * codificado em Base64. No frontend, basta decodificar e disparar o download
 * com {@code URL.createObjectURL(new Blob([...]))}.</p>
 */
@Getter
@Builder
@Schema(description = "Arquivo exportado (PDF/Excel) codificado em Base64 para consumo no Angular")
public class ExportacaoResponse {

    @Schema(description = "Nome sugerido do arquivo, ja com extensao",
            example = "relatorio-pagamento-rodada-15.pdf")
    private final String fileName;

    @Schema(description = "Mime type do arquivo", example = "application/pdf")
    private final String mimeType;

    @Schema(description = "Extensao do arquivo (sem ponto)", example = "pdf",
            allowableValues = {"pdf", "xlsx"})
    private final String extensao;

    @Schema(description = "Formato exportado", example = "PDF",
            allowableValues = {"PDF", "EXCEL"})
    private final String formato;

    @Schema(description = "Tamanho do arquivo em bytes", example = "12345")
    private final long sizeBytes;

    @Schema(description = "Data e hora ISO-8601 em que o arquivo foi gerado",
            example = "2025-04-27T15:30:00")
    private final LocalDateTime geradoEm;

    @Schema(description = "Conteudo binario do arquivo em Base64 (sem prefixo data:)")
    private final String conteudoBase64;

    public static ExportacaoResponse of(byte[] bytes, String fileName, FormatoExportacao formato) {
        return ExportacaoResponse.builder()
                .fileName(fileName)
                .mimeType(formato.getMimeType())
                .extensao(formato.getExtensao())
                .formato(formato.name())
                .sizeBytes(bytes.length)
                .geradoEm(LocalDateTime.now())
                .conteudoBase64(Base64.getEncoder().encodeToString(bytes))
                .build();
    }
}
