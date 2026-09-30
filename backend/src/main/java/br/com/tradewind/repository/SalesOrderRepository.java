package br.com.tradewind.repository;

import br.com.tradewind.domain.OrderStatus;
import br.com.tradewind.domain.SalesOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {

    boolean existsByOrderNumber(String orderNumber);

    @EntityGraph(attributePaths = {"items", "customer"})
    List<SalesOrder> findAllByCustomerId(UUID customerId);

    /**
     * Janela do relatorio. O recorte de data vai para o SQL de proposito:
     * filtrar depois de {@code findAll()} significa trazer a tabela inteira
     * para a memoria do JVM para descartar quase tudo.
     */
    @EntityGraph(attributePaths = {"items"})
    List<SalesOrder> findByCreatedAtAfter(OffsetDateTime since);

    /** Usado pela previsao de demanda: precisa do produto do item para agrupar. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<SalesOrder> findByStatus(OrderStatus status);

    @EntityGraph(attributePaths = {"items", "items.product", "items.inventory", "customer"})
    Optional<SalesOrder> findDetailedById(UUID id);
}