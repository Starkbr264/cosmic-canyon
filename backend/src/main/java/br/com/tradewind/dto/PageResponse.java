package br.com.tradewind.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope de paginacao com contrato estavel.
 *
 * <p>Existe em vez de devolver {@link Page} direto porque o Spring avisa que a
 * serializacao de {@code PageImpl} "nao tem garantia de estabilidade" - mudar
 * essa estrutura quebraria clientes silenciosamente. Com um record proprio o
 * formato fica congelado, documentado no Swagger e seguro para o frontend e
 * para a colecao do Postman dependerem dele.
 */
@Schema(name = "PageResponse", description = "Pagina de resultados")
public record PageResponse<T>(
        @Schema(description = "Itens da pagina atual")
        List<T> content,

        @Schema(description = "Numero da pagina (base 0)", example = "0")
        int page,

        @Schema(description = "Tamanho da pagina", example = "20")
        int size,

        @Schema(description = "Total de itens em todos os filtros", example = "5")
        long totalElements,

        @Schema(description = "Total de paginas", example = "1")
        int totalPages,

        @Schema(description = "Primeira pagina?", example = "true")
        boolean first,

        @Schema(description = "Ultima pagina?", example = "true")
        boolean last
) {
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
