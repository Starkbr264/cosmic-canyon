package br.com.tradewind.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "inventory_locations") @Getter @Setter @NoArgsConstructor
public class InventoryLocation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private String code;
    @Column(nullable = false) private String name;
    @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(nullable = false) private boolean active;
}
