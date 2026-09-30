package br.com.tradewind.dto;
import br.com.tradewind.domain.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public record OrderResponse(UUID id, String orderNumber, OrderStatus status, String currency,
                            BigDecimal subtotal, BigDecimal taxTotal, BigDecimal grandTotal,
                            String countryCode, String locale, OffsetDateTime createdAt, List<Item> items) {
    public record Item(UUID productId, String sku, String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
    public static OrderResponse from(SalesOrder order) {
        return new OrderResponse(order.getId(), order.getOrderNumber(), order.getStatus(), order.getCurrency(),
                order.getSubtotal(), order.getTaxTotal(), order.getGrandTotal(), order.getCountryCode(), order.getLocale(), order.getCreatedAt(),
                order.getItems().stream().map(i -> new Item(i.getProduct().getId(), i.getProductSku(), i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal())).toList());
    }
}
