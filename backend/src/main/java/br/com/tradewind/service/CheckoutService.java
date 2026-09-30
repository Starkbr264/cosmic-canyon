package br.com.tradewind.service;

import br.com.tradewind.domain.Customer;
import br.com.tradewind.domain.Inventory;
import br.com.tradewind.domain.InventoryMovement;
import br.com.tradewind.domain.OrderItem;
import br.com.tradewind.domain.OrderStatus;
import br.com.tradewind.domain.Payment;
import br.com.tradewind.domain.PaymentStatus;
import br.com.tradewind.domain.Product;
import br.com.tradewind.domain.SalesOrder;
import br.com.tradewind.dto.CreateOrderRequest;
import br.com.tradewind.dto.OrderItemRequest;
import br.com.tradewind.dto.SimulatePaymentRequest;
import br.com.tradewind.exception.BusinessRuleException;
import br.com.tradewind.exception.ResourceNotFoundException;
import br.com.tradewind.repository.CustomerRepository;
import br.com.tradewind.repository.InventoryMovementRepository;
import br.com.tradewind.repository.InventoryRepository;
import br.com.tradewind.repository.PaymentRepository;
import br.com.tradewind.repository.ProductRepository;
import br.com.tradewind.repository.SalesOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Fluxo de compra demonstrativo.
 *
 * <p>Duas garantias de consistencia sustentam o checkout:
 *
 * <ul>
 *   <li>A reserva de estoque usa {@code SELECT ... FOR UPDATE} por
 *       produto+localizacao, entao dois checkouts concorrentes nunca vendem a
 *       mesma unidade.</li>
 *   <li>Cada item guarda a linha de {@code inventory} que ele reservou
 *       ({@code order_items.inventory_id}). A liberacao no pagamento lanca
 *       exatamente a linha que foi baixada, sem adivinhacao.</li>
 * </ul>
 *
 * <p>Nao existe integracao com gateway: o pagamento e um registro local com o
 * resultado escolhido para a demonstracao.
 */
@Service
@Transactional
public class CheckoutService {

    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SalesOrderRepository orders;
    private final InventoryRepository inventory;
    private final InventoryMovementRepository movements;
    private final PaymentRepository payments;

    public CheckoutService(ProductRepository products,
                           CustomerRepository customers,
                           SalesOrderRepository orders,
                           InventoryRepository inventory,
                           InventoryMovementRepository movements,
                           PaymentRepository payments) {
        this.products = products;
        this.customers = customers;
        this.orders = orders;
        this.inventory = inventory;
        this.movements = movements;
        this.payments = payments;
    }

    public SalesOrder createOrder(CreateOrderRequest request) {
        Customer customer = customers
                .findByEmailIgnoreCase(request.customerEmail().trim())
                .orElseGet(() -> customers.save(newCustomer(request)));

        SalesOrder order = new SalesOrder();
        order.setOrderNumber(newOrderNumber());
        order.setCustomer(customer);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setCurrency(request.currency());
        order.setCountryCode(request.countryCode());
        order.setLocale(request.locale());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemRequest requestedItem : request.items()) {
            OrderItem item = reserveItem(requestedItem);
            order.addItem(item);
            subtotal = subtotal.add(item.getLineTotal());
        }
        order.setSubtotal(subtotal);

        // O motor fiscal por pais ainda nao existe nesta demonstracao. O campo
        // fica zerado em vez de inventar aliquota, para o total nao parecer
        // uma cobranca valida.
        order.setTaxTotal(BigDecimal.ZERO.setScale(2));
        order.setGrandTotal(subtotal.add(order.getTaxTotal()).setScale(2, RoundingMode.HALF_UP));

        SalesOrder saved = orders.save(order);
        saved.getItems()
                .forEach(item -> registerMovement(item, saved, -item.getQuantity(),
                        "RESERVATION", "Reserva gerada pelo checkout"));

        return saved;
    }

    @Transactional(readOnly = true)
    public SalesOrder getOrder(UUID id) {
        return orders.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado: " + id));
    }

    /**
     * Registra o resultado simulado do pagamento e propaga o resultado para o
     * pedido e para o estoque reservado.
     */
    public Payment simulatePayment(UUID orderId, SimulatePaymentRequest request) {
        SalesOrder order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT
                && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw new BusinessRuleException(
                    "O pedido " + order.getOrderNumber() + " nao aceita novo pagamento");
        }

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(request.method());
        payment.setStatus(request.outcome());
        payment.setAmount(order.getGrandTotal());
        payment.setCurrency(order.getCurrency());
        payment.setSimulationReference(newSimulationReference());
        payments.save(payment);

        switch (request.outcome()) {
            case APPROVED -> {
                order.setStatus(OrderStatus.CONFIRMED);
                // Reserva vira consumo: sai da coluna reserved e nao volta para available.
                order.getItems().forEach(item ->
                        item.getInventory().setReservedQuantity(
                                item.getInventory().getReservedQuantity() - item.getQuantity()));
            }
            case DECLINED -> {
                order.setStatus(OrderStatus.PAYMENT_DECLINED);
                order.getItems().forEach(item -> releaseReservation(item, order));
            }
            case PENDING -> order.setStatus(OrderStatus.PAYMENT_PENDING);
        }

        return payment;
    }

    private OrderItem reserveItem(OrderItemRequest requestedItem) {
        Product product = products.findById(requestedItem.productId())
                .orElseThrow(() -> ResourceNotFoundException.product(requestedItem.productId()));

        if (!product.isActive()) {
            throw new BusinessRuleException("Produto indisponivel: " + product.getSku());
        }

        Inventory stock = inventory.lockAvailableByProduct(product.getId()).stream()
                .filter(candidate -> candidate.getAvailableQuantity() >= requestedItem.quantity())
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "Estoque insuficiente para " + product.getSku()));

        stock.setAvailableQuantity(stock.getAvailableQuantity() - requestedItem.quantity());
        stock.setReservedQuantity(stock.getReservedQuantity() + requestedItem.quantity());

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setInventory(stock);
        item.setProductSku(product.getSku());
        item.setProductName(product.getName());
        item.setQuantity(requestedItem.quantity());
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice()
                .multiply(BigDecimal.valueOf(requestedItem.quantity()))
                .setScale(2, RoundingMode.HALF_UP));
        return item;
    }

    private void releaseReservation(OrderItem item, SalesOrder order) {
        Inventory stock = item.getInventory();
        stock.setReservedQuantity(stock.getReservedQuantity() - item.getQuantity());
        stock.setAvailableQuantity(stock.getAvailableQuantity() + item.getQuantity());
        registerMovement(item, order, item.getQuantity(),
                "RELEASE", "Reserva liberada por pagamento simulado recusado");
    }

    private void registerMovement(OrderItem item, SalesOrder order, int quantity, String type, String reason) {
        InventoryMovement movement = new InventoryMovement();
        movement.setInventory(item.getInventory());
        movement.setOrder(order);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReason(reason);
        movements.save(movement);
    }

    private Customer newCustomer(CreateOrderRequest request) {
        Customer customer = new Customer();
        customer.setEmail(request.customerEmail().trim().toLowerCase());
        customer.setDisplayName(request.customerName().trim());
        customer.setLocale(request.locale());
        customer.setCountryCode(request.countryCode());
        // Consentimento de marketing e separado do cadastro e nasce falso
        // (minimizacao de dado, LGPD art. 6 / GDPR art. 5.1.c).
        customer.setMarketingConsent(false);
        return customer;
    }

    /**
     * Numero curto legivel por humanos. A colisao e improvavel (8 hex de UUID),
     * mas o unic constraint continua sendo a garantia real - aqui so evitamos
     * que o proprio usuario veja um 500 em um caso que daria para repetir.
     */
    private String newOrderNumber() {
        String candidate;
        do {
            candidate = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (orders.existsByOrderNumber(candidate));
        return candidate;
    }

    private String newSimulationReference() {
        return "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
