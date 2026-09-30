package br.com.tradewind.dto;
import br.com.tradewind.domain.Payment;
import br.com.tradewind.domain.PaymentMethod;
import br.com.tradewind.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
public record PaymentResponse(UUID id, UUID orderId, PaymentMethod method, PaymentStatus status,
                              BigDecimal amount, String currency, String simulationReference, OffsetDateTime processedAt) {
    public static PaymentResponse from(Payment payment) { return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getMethod(), payment.getStatus(), payment.getAmount(), payment.getCurrency(), payment.getSimulationReference(), payment.getProcessedAt()); }
}
