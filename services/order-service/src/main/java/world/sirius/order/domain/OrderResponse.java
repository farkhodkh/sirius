package world.sirius.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, String status, BigDecimal totalAmount, String currency,
                            Instant createdAt, List<ItemResponse> items) {
    public record ItemResponse(String sku, String name, int quantity, BigDecimal unitPrice) {}
    public static OrderResponse from(Order o) { /* маппинг сущности в DTO */ return null; }
}
