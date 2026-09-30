package br.com.tradewind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity @Table(name = "payments") @Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id", nullable = false) private SalesOrder order;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentMethod method;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private PaymentStatus status;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "simulation_reference", nullable = false, unique = true) private String simulationReference;
    @Column(name = "processed_at", nullable = false) private OffsetDateTime processedAt;
    @PrePersist void created() { if (processedAt == null) processedAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
