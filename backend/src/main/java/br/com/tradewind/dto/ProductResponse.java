package br.com.tradewind.dto;

import br.com.tradewind.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "ProductResponse", description = "Produto retornado pela API")
public record ProductResponse(
        @Schema(description = "Identificador do produto", example = "9f1c2b7e-6d2a-4c0e-9a3b-8f2d5c1e7a40")
        UUID id,

        @Schema(example = "SKU-001")
        String sku,

        @Schema(example = "Cafe Torrado 500g")
        String name,

        @Schema(example = "Torra media, notas de chocolate")
        String description,

        @Schema(example = "39.90")
        BigDecimal price,

        @Schema(example = "true")
        boolean active,

        @Schema(example = "2026-09-25T12:00:00-03:00")
        OffsetDateTime createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                product.getCreatedAt()
        );
    }
}
