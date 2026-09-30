package br.com.tradewind.dto;

import br.com.tradewind.domain.Inventory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "InventoryView", description = "Saldo de um produto em uma localizacao")
public record InventoryView(
        UUID id,
        UUID productId,
        String sku,
        String productName,
        UUID locationId,
        String locationCode,
        int availableQuantity,
        int reservedQuantity,
        int reorderPoint,
        boolean lowStock) {

    public static InventoryView from(Inventory inventory) {
        int available = inventory.getAvailableQuantity();
        return new InventoryView(
                inventory.getId(),
                inventory.getProduct().getId(),
                inventory.getProduct().getSku(),
                inventory.getProduct().getName(),
                inventory.getLocation().getId(),
                inventory.getLocation().getCode(),
                available,
                inventory.getReservedQuantity(),
                inventory.getReorderPoint(),
                available <= inventory.getReorderPoint());
    }
}
