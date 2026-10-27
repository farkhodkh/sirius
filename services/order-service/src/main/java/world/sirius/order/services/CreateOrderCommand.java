package world.sirius.order.services;

import java.math.BigDecimal;
import java.util.List;

import world.sirius.order.domain.OrderItem;
import world.sirius.order.web.CreateOrderRequest;

/** Входные данные для создания заказа: не зависит от формата HTTP-запроса. */
public record CreateOrderCommand(String currency, List<Item> items) {

    public record Item(String sku, String name, int quantity, BigDecimal unitPrice) {}

    /** Строит команду из веб-DTO. */
    public static CreateOrderCommand from(CreateOrderRequest req) {
        return new CreateOrderCommand(
                req.currency(),
                req.items().stream()
                        .map(i -> new Item(i.sku(), i.name(), i.quantity(), i.unitPrice()))
                        .toList());
    }

    /** Преобразует позиции команды в сущности JPA. */
    public List<OrderItem> toItems() {
        return items.stream()
                .map(i -> new OrderItem(i.sku(), i.name(), i.quantity(), i.unitPrice()))
                .toList();
    }
}