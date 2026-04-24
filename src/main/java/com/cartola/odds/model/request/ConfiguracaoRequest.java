package com.cartola.odds.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Campos a atualizar na configuracao. Todos sao opcionais — envie apenas o que deseja alterar.")
public class ConfiguracaoRequest {

    @DecimalMin(value = "1.01", message = "oddLimite deve ser maior que 1.0")
    @Schema(description = "Odd maxima para considerar um time favorito", example = "2.5")
    private Double oddLimite;

    @DecimalMin(value = "0.0", message = "Peso deve ser >= 0.0")
    @DecimalMax(value = "1.0", message = "Peso deve ser <= 1.0")
    @Schema(description = "Peso da media de pontos da temporada (0.0–1.0)", example = "0.40")
    private Double pesoMediaPontos;

    @DecimalMin(value = "0.0", message = "Peso deve ser >= 0.0")
    @DecimalMax(value = "1.0", message = "Peso deve ser <= 1.0")
    @Schema(description = "Peso da valorizacao (0.0–1.0)", example = "0.20")
    private Double pesoValorizacao;

    @DecimalMin(value = "0.0", message = "Peso deve ser >= 0.0")
    @DecimalMax(value = "1.0", message = "Peso deve ser <= 1.0")
    @Schema(description = "Peso do desempenho recente (0.0–1.0)", example = "0.20")
    private Double pesoDesempenho;

    @DecimalMin(value = "0.0", message = "Peso deve ser >= 0.0")
    @DecimalMax(value = "1.0", message = "Peso deve ser <= 1.0")
    @Schema(description = "Peso do bonus de mandante (0.0–1.0)", example = "0.10")
    private Double pesoFatorCasa;

    @DecimalMin(value = "0.0", message = "Peso deve ser >= 0.0")
    @DecimalMax(value = "1.0", message = "Peso deve ser <= 1.0")
    @Schema(description = "Peso do bonus de time favorito pelas odds (0.0–1.0)", example = "0.10")
    private Double pesoTimeFavorito;

    @Min(value = 0, message = "Quantidade de GOL deve ser >= 0")
    @Schema(description = "Numero de goleiros na formacao", example = "1")
    private Integer formacaoGol;

    @Min(value = 0, message = "Quantidade de LAT deve ser >= 0")
    @Schema(description = "Numero de laterais na formacao", example = "2")
    private Integer formacaoLat;

    @Min(value = 0, message = "Quantidade de ZAG deve ser >= 0")
    @Schema(description = "Numero de zagueiros na formacao", example = "2")
    private Integer formacaoZag;

    @Min(value = 0, message = "Quantidade de MEI deve ser >= 0")
    @Schema(description = "Numero de meias na formacao", example = "3")
    private Integer formacaoMei;

    @Min(value = 0, message = "Quantidade de ATA deve ser >= 0")
    @Schema(description = "Numero de atacantes na formacao", example = "3")
    private Integer formacaoAta;

    @Min(value = 0, message = "Quantidade de TEC deve ser >= 0")
    @Schema(description = "Numero de tecnicos na formacao", example = "1")
    private Integer formacaoTec;
}
