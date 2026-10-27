package world.sirius.order.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import world.sirius.order.domain.Order;

/** Краткое представление заказа для списков: без позиций (они загружаются лениво и в списке не нужны). */
public record OrderSummary(UUID id, String status, BigDecimal totalAmount, String currency, Instant createdAt) {

    public static OrderSummary from(Order o) {
        return new OrderSummary(o.getId(), o.getStatus().name(), o.getTotalAmount(), o.getCurrency(), o.getCreatedAt());
    }
}
