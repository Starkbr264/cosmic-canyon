package br.com.tradewind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "inventory", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "location_id"}))
@Getter @Setter @NoArgsConstructor
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "location_id", nullable = false) private InventoryLocation location;
    @Column(name = "available_quantity", nullable = false) private int availableQuantity;
    @Column(name = "reserved_quantity", nullable = false) private int reservedQuantity;
    @Column(name = "reorder_point", nullable = false) private int reorderPoint;
    @Version private long version;
}
