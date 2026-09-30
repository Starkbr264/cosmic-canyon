package br.com.tradewind.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "ProductRequest", description = "Payload de criacao/atualizacao de produto")
public record ProductRequest(

        @NotBlank
        @Size(max = 64)
        @Schema(description = "Codigo de estoque (unico)", example = "SKU-001", requiredMode = Schema.RequiredMode.REQUIRED)
        String sku,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "Nome comercial", example = "Cafe Torrado 500g", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Size(max = 1000)
        @Schema(description = "Descricao livre", example = "Torra media, notas de chocolate")
        String description,

        @NotNull
        @DecimalMin(value = "0.00", inclusive = true)
        @Schema(description = "Preco unitario", example = "39.90", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal price,

        @Schema(description = "Disponivel na loja", example = "true", defaultValue = "true")
        Boolean active
) {
}
