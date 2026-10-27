package world.sirius.order.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import world.sirius.order.domain.Order;

public record OrderResponse(UUID id, String status, BigDecimal totalAmount, String currency,
                            Instant createdAt, List<ItemResponse> items) {

    public record ItemResponse(String sku, String name, int quantity, BigDecimal unitPrice) {}

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getStatus().name(), o.getTotalAmount(), o.getCurrency(),
                o.getCreatedAt(),
                o.getItems().stream()
                        .map(i -> new ItemResponse(i.getSku(), i.getName(), i.getQuantity(), i.getUnitPrice()))
                        .toList());
    }
}
