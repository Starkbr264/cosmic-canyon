package br.com.tradewind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity @Table(name = "customers") @Getter @Setter @NoArgsConstructor
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 320) private String email;
    @Column(name = "display_name", nullable = false, length = 160) private String displayName;
    @Column(nullable = false, length = 35) private String locale;
    @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(name = "marketing_consent", nullable = false) private boolean marketingConsent;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist void created() { if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
