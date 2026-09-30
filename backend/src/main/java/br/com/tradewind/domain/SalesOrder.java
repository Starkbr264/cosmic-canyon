package br.com.tradewind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity @Table(name = "sales_orders") @Getter @Setter @NoArgsConstructor
public class SalesOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "order_number", nullable = false, unique = true) private String orderNumber;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id") private Customer customer;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;
    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2) private BigDecimal taxTotal;
    @Column(name = "grand_total", nullable = false, precision = 14, scale = 2) private BigDecimal grandTotal;
    @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(nullable = false, length = 35) private String locale;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true) private List<OrderItem> items = new ArrayList<>();
    @PrePersist void create() { var now = OffsetDateTime.now(ZoneOffset.UTC); if (createdAt == null) createdAt = now; updatedAt = now; }
    @PreUpdate void update() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public void addItem(OrderItem item) { item.setOrder(this); items.add(item); }
}
