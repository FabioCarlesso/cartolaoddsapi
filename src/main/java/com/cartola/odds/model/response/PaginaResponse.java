package com.cartola.odds.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envelope de paginacao da API.
 *
 * <p>Existe para nao serializar o {@code Page} do Spring Data direto na resposta: o JSON
 * daquela classe e um detalhe interno, muda entre versoes do framework e o proprio Spring
 * avisa disso no log. Aqui o contrato e nosso, e este e o formato que os proximos
 * endpoints paginados devem reusar.
 *
 * @param <T> tipo do DTO de resposta que preenche a pagina
 */
@Getter
@Builder
@Schema(description = "Pagina de resultados")
public class PaginaResponse<T> {

    @Schema(description = "Itens da pagina atual")
    private final List<T> conteudo;

    @Schema(description = "Numero da pagina atual, comecando em 0", example = "0")
    private final int pagina;

    @Schema(description = "Tamanho da pagina solicitado", example = "20")
    private final int tamanho;

    @Schema(description = "Total de itens em todas as paginas", example = "42")
    private final long totalElementos;

    @Schema(description = "Total de paginas", example = "3")
    private final int totalPaginas;

    @Schema(description = "Indica se esta e a ultima pagina", example = "false")
    private final boolean ultima;

    public static <T> PaginaResponse<T> from(Page<T> page) {
        return PaginaResponse.<T>builder()
                .conteudo(page.getContent())
                .pagina(page.getNumber())
                .tamanho(page.getSize())
                .totalElementos(page.getTotalElements())
                .totalPaginas(page.getTotalPages())
                .ultima(page.isLast())
                .build();
    }
}
