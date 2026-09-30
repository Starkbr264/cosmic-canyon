package br.com.tradewind.repository;

import br.com.tradewind.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> { }
