package br.com.tradewind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity @Table(name = "inventory_movements") @Getter @Setter @NoArgsConstructor
public class InventoryMovement {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inventory_id", nullable = false) private Inventory inventory;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id") private SalesOrder order;
    @Column(name = "movement_type", nullable = false) private String movementType;
    @Column(nullable = false) private int quantity;
    @Column(nullable = false) private String reason;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
