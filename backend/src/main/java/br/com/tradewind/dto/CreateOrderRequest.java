package br.com.tradewind.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record CreateOrderRequest(
        @Email @NotBlank @Size(max = 320) String customerEmail,
        @NotBlank @Size(max = 160) String customerName,
        @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?", message = "deve usar locale BCP-47, por exemplo pt-BR") String locale,
        @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotEmpty List<@Valid OrderItemRequest> items) { }
