package world.sirius.order.services;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import world.sirius.order.web.OrderResponse;

/**
 * Чтение карточки заказа с кэшем Redis. Отдельный бин: кэш работает через прокси Spring,
 * поэтому вызов @Cacheable-метода из другого метода ТОГО ЖЕ класса кэш бы обошёл (self-invocation).
 */
@Service
public class OrderReadService {

    private final OrderService orders;

    public OrderReadService(OrderService orders) {
        this.orders = orders;
    }

    /** Ключ включает владельца: иначе один клиент мог бы получить из кэша чужой заказ. null (не найден) не кэшируется. */
    @Cacheable(cacheNames = "order", key = "#customerId + ':' + #id", unless = "#result == null")
    public OrderResponse find(UUID id, UUID customerId) {
        return orders.get(id, customerId).map(OrderResponse::from).orElse(null);
    }

    public OrderService getOrders() {
        return orders;
    }

}
