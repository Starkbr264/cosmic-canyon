package br.com.tradewind.dto;
import br.com.tradewind.domain.PaymentMethod;
import br.com.tradewind.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;
/** Resultado é escolhido só para demonstrar o fluxo; não aceita dados financeiros. */
public record SimulatePaymentRequest(@NotNull PaymentMethod method, @NotNull PaymentStatus outcome) { }
