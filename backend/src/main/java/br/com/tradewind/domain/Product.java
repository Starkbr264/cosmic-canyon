package br.com.tradewind.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Entidade de exemplo. O schema no banco e de responsabilidade do Flyway
 * ({@code db/migration}), nunca do Hibernate - {@code ddl-auto} fica sempre
 * desligado para o schema local e o do Supabase nunca divergirem.
 */
@Entity
@Table(name = "products")
@Schema(name = "Product", description = "Produto do catalogo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    @Schema(description = "Identificador do produto", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @Column(name = "sku", nullable = false, length = 64, unique = true)
    @Schema(description = "Codigo de estoque", example = "SKU-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sku;

    @Column(name = "name", nullable = false, length = 255)
    @Schema(description = "Nome comercial", example = "Cafe Torrado 500g", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Column(name = "description", length = 1000)
    @Schema(description = "Descricao livre", example = "Torra media, notas de chocolate")
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    @Schema(description = "Preco unitario", example = "39.90", minimum = "0.00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    @Schema(description = "Se o produto esta disponivel na loja", example = "true")
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Schema(description = "Data de criacao", accessMode = Schema.AccessMode.READ_ONLY)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
