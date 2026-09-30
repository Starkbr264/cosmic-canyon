package br.com.tradewind.service;

import br.com.tradewind.domain.*;
import br.com.tradewind.dto.CreateOrderRequest;
import br.com.tradewind.dto.OrderItemRequest;
import br.com.tradewind.dto.SimulatePaymentRequest;
import br.com.tradewind.exception.BusinessRuleException;
import br.com.tradewind.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Regras de negocio do checkout testadas sem Spring e sem banco.
 *
 * <p>O que nao esta aqui e o que nao cabe num mock: a garantia de que dois
 * checkouts concorrentes nao vendem a mesma unidade. Isso vem do
 * {@code SELECT ... FOR UPDATE} e e responsabilidade do Postgres - ver
 * {@code ProductRepositoryIT} para o lado de schema e o teste de integracao do
 * fluxo completo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CheckoutService")
class CheckoutServiceTest {

    @Mock private ProductRepository products;
    @Mock private CustomerRepository customers;
    @Mock private SalesOrderRepository orders;
    @Mock private InventoryRepository inventory;
    @Mock private InventoryMovementRepository movements;
    @Mock private PaymentRepository payments;

    @InjectMocks private CheckoutService checkout;

    private static Product product(String sku, String name, String price) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setSku(sku);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setActive(true);
        return p;
    }

    private static Inventory stock(Product product, int available) {
        InventoryLocation location = new InventoryLocation();
        location.setId(UUID.randomUUID());
        location.setCode("BR-SP-01");
        location.setCountryCode("BR");
        location.setActive(true);

        Inventory row = new Inventory();
        row.setId(UUID.randomUUID());
        row.setProduct(product);
        row.setLocation(location);
        row.setAvailableQuantity(available);
        row.setReservedQuantity(0);
        row.setReorderPoint(5);
        return row;
    }

    private CreateOrderRequest request(String email, Product p, int quantity) {
        return new CreateOrderRequest(email, "Cliente Teste", "pt-BR", "BR", "BRL",
                List.of(new OrderItemRequest(p.getId(), quantity)));
    }

    @Nested
    @DisplayName("criacao de pedido")
    class CreateOrder {

        @Test
        @DisplayName("reserva estoque, soma subtotal e deixa o pedido aguardando pagamento")
        void reservesStockAndPricesOrder() {
            Product product = product("SKU-001", "Cafe 500g", "39.90");
            Inventory row = stock(product, 40);
            Customer customer = new Customer();
            customer.setId(UUID.randomUUID());

            when(products.findById(product.getId())).thenReturn(Optional.of(product));
            when(customers.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(customer));
            when(inventory.lockAvailableByProduct(product.getId())).thenReturn(List.of(row));
            when(orders.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            SalesOrder order = checkout.createOrder(request("ana@example.com", product, 2));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            assertThat(order.getGrandTotal()).isEqualByComparingTo("79.80");
            // Disponivel cai e o mesmo valor vai para reservado: a unidade esta
            // comprometida, mas nao foi consumida antes do pagamento.
            assertThat(row.getAvailableQuantity()).isEqualTo(38);
            assertThat(row.getReservedQuantity()).isEqualTo(2);
            verify(movements).save(argThat(m -> m.getMovementType().equals("RESERVATION")
                    && m.getQuantity() == -2));
        }

        @Test
        @DisplayName("impede venda acima do saldo disponivel")
        void rejectsInsufficientStock() {
            Product product = product("SKU-002", "Cafe 1kg", "74.50");
            Inventory row = stock(product, 1);

            when(products.findById(product.getId())).thenReturn(Optional.of(product));
            when(customers.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
            when(inventory.lockAvailableByProduct(product.getId())).thenReturn(List.of(row));

            assertThatThrownBy(() -> checkout.createOrder(request("bruno@example.com", product, 5)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Estoque insuficiente");

            assertThat(row.getAvailableQuantity()).isEqualTo(1);
            verify(orders, never()).save(any());
        }

        @Test
        @DisplayName("impede venda de produto desativado")
        void rejectsInactiveProduct() {
            Product product = product("SKU-005", "Descafeinado", "41.90");
            product.setActive(false);

            when(products.findById(product.getId())).thenReturn(Optional.of(product));
            when(customers.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> checkout.createOrder(request("carla@example.com", product, 1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("indisponivel");

            verify(inventory, never()).lockAvailableByProduct(any());
        }

        @Test
        @DisplayName("escolhe o local com saldo suficiente")
        void picksLocationWithEnoughStock() {
            Product product = product("SKU-003", "Filtro", "18.00");
            Inventory small = stock(product, 2);
            Inventory large = stock(product, 50);

            when(products.findById(product.getId())).thenReturn(Optional.of(product));
            when(customers.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
            // A query ja vem ordenada por saldo decrescente; o filtro final
            // garante o primeiro que cobre a quantidade pedida.
            when(inventory.lockAvailableByProduct(product.getId())).thenReturn(List.of(large, small));
            when(orders.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));

            SalesOrder order = checkout.createOrder(request("dani@example.com", product, 10));

            assertThat(order.getItems().get(0).getInventory()).isSameAs(large);
            assertThat(large.getAvailableQuantity()).isEqualTo(40);
            assertThat(small.getAvailableQuantity()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("pagamento simulado")
    class SimulatedPayment {

        private SalesOrder order() {
            Product product = product("SKU-001", "Cafe 500g", "39.90");
            Inventory row = stock(product, 38);
            row.setReservedQuantity(2);

            SalesOrder order = new SalesOrder();
            order.setId(UUID.randomUUID());
            order.setOrderNumber("ORD-1234ABCD");
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            order.setCurrency("BRL");
            order.setGrandTotal(new BigDecimal("79.80"));
            order.setTaxTotal(BigDecimal.ZERO);

            OrderItem item = new OrderItem();
            item.setId(UUID.randomUUID());
            item.setProduct(product);
            item.setInventory(row);
            item.setProductSku(product.getSku());
            item.setProductName(product.getName());
            item.setQuantity(2);
            item.setUnitPrice(product.getPrice());
            item.setLineTotal(new BigDecimal("79.80"));
            order.addItem(item);
            return order;
        }

        private SalesOrder wire(SalesOrder order) {
            when(orders.findDetailedById(order.getId())).thenReturn(Optional.of(order));
            return order;
        }

        @Test
        @DisplayName("aprovado confirma o pedido e consome a reserva")
        void approvedConfirmsOrderAndConsumesReservation() {
            SalesOrder order = wire(order());

            Payment payment = checkout.simulatePayment(order.getId(),
                    new SimulatePaymentRequest(PaymentMethod.PIX, PaymentStatus.APPROVED));

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.getSimulationReference()).startsWith("SIM-");
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            // A reserva vira consumo: nao volta para disponivel.
            assertThat(order.getItems().get(0).getInventory().getReservedQuantity()).isZero();
            assertThat(order.getItems().get(0).getInventory().getAvailableQuantity()).isEqualTo(38);
        }

        @Test
        @DisplayName("recusado devolve a unidade ao disponivel")
        void declinedReleasesStock() {
            SalesOrder order = wire(order());

            checkout.simulatePayment(order.getId(),
                    new SimulatePaymentRequest(PaymentMethod.CARD, PaymentStatus.DECLINED));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
            assertThat(order.getItems().get(0).getInventory().getAvailableQuantity()).isEqualTo(40);
            assertThat(order.getItems().get(0).getInventory().getReservedQuantity()).isZero();
            verify(movements).save(argThat(m -> m.getMovementType().equals("RELEASE")));
        }

        @Test
        @DisplayName("pendente mantem a reserva e deixa o pedido aguardando")
        void pendingKeepsReservation() {
            SalesOrder order = wire(order());

            checkout.simulatePayment(order.getId(),
                    new SimulatePaymentRequest(PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
            assertThat(order.getItems().get(0).getInventory().getReservedQuantity()).isEqualTo(2);
        }

        @Test
        @DisplayName("bloqueia segundo pagamento em pedido ja confirmado")
        void blocksSecondPayment() {
            SalesOrder order = order();
            order.setStatus(OrderStatus.CONFIRMED);
            wire(order);

            assertThatThrownBy(() -> checkout.simulatePayment(order.getId(),
                    new SimulatePaymentRequest(PaymentMethod.PIX, PaymentStatus.APPROVED)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("nao aceita novo pagamento");

            verify(payments, never()).save(any());
        }
    }
}
