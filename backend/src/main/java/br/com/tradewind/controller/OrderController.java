package br.com.tradewind.controller;

import br.com.tradewind.dto.*;
import br.com.tradewind.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/orders")
@Tag(name = "Pedidos", description = "Checkout acadêmico com estoque e pagamento simulado")
public class OrderController {
    private final CheckoutService checkout;
    public OrderController(CheckoutService checkout) { this.checkout = checkout; }
    @PostMapping @Operation(summary = "Cria pedido e reserva estoque")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request, UriComponentsBuilder uri) {
        var order = checkout.createOrder(request);
        URI location = uri.path("/api/v1/orders/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }
    @GetMapping("/{id}") @Operation(summary = "Consulta pedido")
    public OrderResponse get(@PathVariable UUID id) { return OrderResponse.from(checkout.getOrder(id)); }
    @PostMapping("/{id}/payments") @Operation(summary = "Simula resultado de pagamento, sem dados financeiros")
    public PaymentResponse pay(@PathVariable UUID id, @Valid @RequestBody SimulatePaymentRequest request) {
        return PaymentResponse.from(checkout.simulatePayment(id, request));
    }
}
