package br.com.tradewind.service;

import br.com.tradewind.domain.Inventory;
import br.com.tradewind.domain.InventoryMovement;
import br.com.tradewind.domain.OrderItem;
import br.com.tradewind.domain.OrderStatus;
import br.com.tradewind.domain.SalesOrder;
import br.com.tradewind.dto.InventoryView;
import br.com.tradewind.dto.StockAdjustmentRequest;
import br.com.tradewind.exception.BusinessRuleException;
import br.com.tradewind.exception.ResourceNotFoundException;
import br.com.tradewind.repository.InventoryMovementRepository;
import br.com.tradewind.repository.InventoryRepository;
import br.com.tradewind.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Saldo em estoque e alerta de reposicao.
 *
 * <p>Separado do {@link CustomerService} porque sao assuntos distintos: um cuida
 * de pessoa, o outro de mercadoria. Ficarem juntos seria o caminho mais curto
 * para um servico que faz duas coisas sem relacao.
 *
 * <p>Os metodos de leitura devolvem DTO, nao entidade. O mapeamento acontece
 * dentro da transacao justamente porque ele le produto e localizacao — devolver
 * a entidade empurraria esse trabalho para o controller, onde a sessao ja
 * acabou.
 */
@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventory;
    private final InventoryMovementRepository movements;
    private final SalesOrderRepository orders;

    public InventoryService(InventoryRepository inventory,
                            InventoryMovementRepository movements,
                            SalesOrderRepository orders) {
        this.inventory = inventory;
        this.movements = movements;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public List<InventoryView> listInventory(boolean onlyLowStock) {
        return inventory.findAllWithRefs().stream()
                .filter(row -> !onlyLowStock || isLowStock(row))
                .map(InventoryView::from)
                .toList();
    }

    public InventoryView adjust(UUID inventoryId, StockAdjustmentRequest request) {
        if (request.quantity() == 0) {
            throw new BusinessRuleException(
                    "Quantidade zero nao movimenta estoque: informe a diferenca ou um valor diferente de zero");
        }

        Inventory stock = inventory.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Saldo nao encontrado: " + inventoryId));

        int updated = stock.getAvailableQuantity() + request.quantity();
        if (updated < 0) {
            throw new BusinessRuleException("Ajuste deixaria o saldo negativo em "
                    + stock.getLocation().getCode());
        }
        stock.setAvailableQuantity(updated);

        InventoryMovement movement = new InventoryMovement();
        movement.setInventory(stock);
        movement.setMovementType(movementType(request.quantity()));
        movement.setQuantity(request.quantity());
        movement.setReason(request.reason());
        movements.save(movement);
        return InventoryView.from(stock);
    }

    /** Entrada de mercadoria e diferente de correcao de contagem. */
    private static String movementType(int quantity) {
        if (quantity > 0) {
            return "RESTOCK";
        }
        return "ADJUSTMENT";
    }

    /**
     * Previsao de demanda por media movel das saidas confirmadas.
     *
     * <p>Deliberadamente nao e um modelo de IA treinado: com o volume deste
     * projeto, media movel de 7 dias estima melhor do que qualquer modelo e
     * explica o numero. A assinatura e o ponto de troca — substituir por um
     * forecast treinado e trocar o corpo do metodo, sem tocar nos controllers.
     */
    @Transactional(readOnly = true)
    public List<ForecastRow> forecast(int windowDays) {
        List<SalesOrder> confirmed = orders.findByStatus(OrderStatus.CONFIRMED);
        LocalDate since = LocalDate.now(ZoneOffset.UTC).minusDays(windowDays);

        Map<UUID, Integer> quantityByProduct = confirmed.stream()
                .filter(order -> !order.getCreatedAt().toLocalDate().isBefore(since))
                .flatMap(order -> order.getItems().stream())
                .collect(Collectors.groupingBy(item -> item.getProduct().getId(),
                        Collectors.summingInt(OrderItem::getQuantity)));

        return inventory.findAllWithRefs().stream()
                .map(row -> {
                    int sold = quantityByProduct.getOrDefault(row.getProduct().getId(), 0);
                    double dailyRate = (double) sold / windowDays;
                    return new ForecastRow(row.getProduct().getSku(), row.getProduct().getName(),
                            row.getLocation().getCode(), sold, round(dailyRate),
                            row.getAvailableQuantity(), row.getReorderPoint(),
                            dailyRate <= 0 ? 0 : (int) Math.ceil(row.getReorderPoint() / dailyRate),
                            daysOfCover(row.getAvailableQuantity(), dailyRate));
                })
                .sorted(Comparator.comparingInt(ForecastRow::daysOfCover))
                .toList();
    }

    /**
     * Sem saida no periodo o estoque esta parado, entao "infinito" e a resposta
     * honesta — mas vaza para o JSON como {@code 2147483647}. O teto de mil dias
     * e o valor de painel: passa qualquer alerta de reposicao e continua
     * ordenando a lista.
     */
    private static int daysOfCover(int available, double dailyRate) {
        if (dailyRate <= 0) {
            return NO_DEMAND_DAYS;
        }
        return Math.min((int) (available / dailyRate), NO_DEMAND_DAYS);
    }

    /** Sinaliza "sem demanda na janela", nao "vai durar muito". */
    public static final int NO_DEMAND_DAYS = 1000;

    private boolean isLowStock(Inventory row) {
        return row.getAvailableQuantity() <= row.getReorderPoint();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record ForecastRow(String sku, String productName, String locationCode,
                              int unitsSoldInWindow, double dailyRate, int availableQuantity,
                              int reorderPoint, int suggestedReorder, int daysOfCover) {
    }
}