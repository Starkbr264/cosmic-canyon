package br.com.tradewind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "order_items") @Getter @Setter @NoArgsConstructor
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false) private SalesOrder order;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "product_id", nullable = false) private Product product;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inventory_id", nullable = false) private Inventory inventory;
    @Column(name = "product_sku", nullable = false) private String productSku;
    @Column(name = "product_name", nullable = false) private String productName;
    @Column(nullable = false) private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "line_total", nullable = false, precision = 14, scale = 2) private BigDecimal lineTotal;
}
