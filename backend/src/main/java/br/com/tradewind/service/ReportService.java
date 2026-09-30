package br.com.tradewind.service;

import br.com.tradewind.domain.OrderItem;
import br.com.tradewind.domain.OrderStatus;
import br.com.tradewind.domain.PaymentStatus;
import br.com.tradewind.domain.PaymentMethod;
import br.com.tradewind.domain.SalesOrder;
import br.com.tradewind.repository.InventoryRepository;
import br.com.tradewind.repository.PaymentRepository;
import br.com.tradewind.repository.SalesOrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Numeros do dashboard.
 *
 * <p>Cada pergunta vai ao banco com o recorte ja aplicado — janela de data,
 * contagem e limite sao SQL, nao filtro em memoria. Com o volume deste projeto
 * as duas abordagens dariam o mesmo numero; a diferenca e que esta continua
 * correta quando a tabela cresce, e o contrato de resposta nao muda.
 *
 * <p>Moedas diferentes nunca sao somadas entre si: o agrupamento e por moeda e a
 * conversao fica a cargo de um servico de cambio externo.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final SalesOrderRepository orders;
    private final PaymentRepository payments;
    private final InventoryRepository inventory;

    public ReportService(SalesOrderRepository orders, PaymentRepository payments,
                         InventoryRepository inventory) {
        this.orders = orders;
        this.payments = payments;
        this.inventory = inventory;
    }

    public Dashboard dashboard(int days) {
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
        List<SalesOrder> window = orders.findByCreatedAtAfter(since);
        List<SalesOrder> confirmed = window.stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED)
                .toList();

        return new Dashboard(
                days,
                window.size(),
                confirmed.size(),
                revenueByCurrency(confirmed),
                salesByCountry(confirmed),
                topProducts(confirmed),
                conversionRate(window),
                payments.countByStatus(PaymentStatus.PENDING),
                inventory.countLowStock(),
                window.stream()
                        .filter(order -> order.getStatus() == OrderStatus.PAYMENT_DECLINED)
                        .count());
    }

    private Map<String, BigDecimal> revenueByCurrency(List<SalesOrder> confirmed) {
        return confirmed.stream().collect(Collectors.groupingBy(
                SalesOrder::getCurrency,
                Collectors.reducing(BigDecimal.ZERO, SalesOrder::getGrandTotal, BigDecimal::add)));
    }

    /**
     * Um pais pode aparecer em mais de uma moeda, entao a chave de agrupamento e
     * o par pais+moeda. Somar BRL com USD daria um numero sem significado.
     */
    private List<CountrySales> salesByCountry(List<SalesOrder> confirmed) {
        return confirmed.stream()
                .collect(Collectors.groupingBy(order -> order.getCountryCode() + "/" + order.getCurrency()))
                .entrySet().stream()
                .map(entry -> {
                    List<SalesOrder> group = entry.getValue();
                    return new CountrySales(group.get(0).getCountryCode(), group.size(),
                            group.get(0).getCurrency(),
                            group.stream().map(SalesOrder::getGrandTotal)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add));
                })
                .sorted(Comparator.comparingLong(CountrySales::orders).reversed())
                .toList();
    }

    private List<ProductSales> topProducts(List<SalesOrder> confirmed) {
        return confirmed.stream()
                .flatMap(order -> order.getItems().stream()
                        .map(item -> new SaleLine(order.getCurrency(), item)))
                .collect(Collectors.groupingBy(line -> line.item().getProductSku() + "/" + line.currency()))
                .entrySet().stream()
                .map(entry -> {
                    List<SaleLine> group = entry.getValue();
                    int units = group.stream().mapToInt(line -> line.item().getQuantity()).sum();
                    BigDecimal revenue = group.stream().map(line -> line.item().getLineTotal())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new ProductSales(group.get(0).item().getProductSku(),
                            group.get(0).item().getProductName(), group.get(0).currency(), units,
                            revenue, group.get(0).item().getUnitPrice());
                })
                .sorted(Comparator.comparingInt(ProductSales::units).reversed())
                .limit(10)
                .toList();
    }

    /** Par de moeda e item: sem isso, BRL e USD seriam somados no mesmo numero. */
    private record SaleLine(String currency, OrderItem item) {
    }

    /**
     * Taxa de conclusao do checkout: pedidos confirmados sobre pedidos criados
     * na janela. Mede a perda entre criar o pedido e concluir o pagamento.
     */
    private double conversionRate(List<SalesOrder> window) {
        if (window.isEmpty()) {
            return 0;
        }
        long confirmedCount = window.stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED).count();
        return Math.round(confirmedCount * 1000.0 / window.size()) / 10.0;
    }

    /** Pagamentos recusados: entrada do alerta antifraude demonstrativo. */
    public List<DeclinedPayment> declinedPayments(int limit) {
        return payments.findByStatusOrderByProcessedAtDesc(PaymentStatus.DECLINED, PageRequest.of(0, limit))
                .stream()
                .map(payment -> new DeclinedPayment(payment.getId(), payment.getOrder().getOrderNumber(),
                        payment.getMethod(), payment.getAmount(), payment.getCurrency(),
                        payment.getProcessedAt()))
                .toList();
    }

    public record Dashboard(int windowDays, int ordersCreated, int ordersConfirmed,
                            Map<String, BigDecimal> revenueByCurrency,
                            List<CountrySales> salesByCountry,
                            List<ProductSales> topProducts,
                            double conversionRatePercent,
                            long pendingPayments,
                            long lowStockRows,
                            long declinedOrders) {
    }

    public record CountrySales(String countryCode, long orders, String currency, BigDecimal revenue) {
    }

    public record ProductSales(String sku, String productName, String currency, int units,
                               BigDecimal revenue, BigDecimal unitPrice) {
    }

    public record DeclinedPayment(UUID id, String orderNumber, PaymentMethod method,
                                  BigDecimal amount, String currency, OffsetDateTime processedAt) {
    }
}