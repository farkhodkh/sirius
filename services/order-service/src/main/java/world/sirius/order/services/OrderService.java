package world.sirius.order.services;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import world.sirius.order.domain.Order;
import world.sirius.order.domain.OutboxEvent;
import world.sirius.order.repository.OrderRepository;
import world.sirius.order.repository.OutboxRepository;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public OrderService(OrderRepository orders, OutboxRepository outbox, ObjectMapper json) {
        this.orders = orders;
        this.outbox = outbox;
        this.json = json;
    }

    // readOnly=true: Hibernate не делает dirty checking, драйвер может использовать
    // read-only режим → быстрее и безопаснее
    @Transactional(readOnly = true)
    public Page<Order> list(UUID customerId, Pageable p) {
        return orders.findByCustomerId(customerId, p);
    }

    // Одна транзакция = атомарно: заказ + строка outbox. Либо обе записи, либо ни
    // одной.
    @Transactional
    public Order create(UUID customerId, String email, CreateOrderCommand cmd, String idemKey) {
        // 1) идемпотентность: повтор запроса (сеть мобильная, клиент ретраит!) вернёт
        // тот же заказ
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, idemKey);
        if (existing.isPresent())
            return existing.get();

        Order order = Order.create(customerId, email, cmd.currency(), idemKey, cmd.toItems());
        try {
            orders.saveAndFlush(order); // flush сразу: нарушение UNIQUE узнаем здесь, а не при коммите
        } catch (DataIntegrityViolationException race) {
            // два одинаковых запроса пришли одновременно: проиграл гонку → читаем
            // победителя
            return orders.findByCustomerIdAndIdempotencyKey(customerId, idemKey).orElseThrow(() -> race);
        }
        outbox.save(OutboxEvent.orderCreated(order, json)); // в ТОЙ ЖЕ транзакции
        return order;
    }
}
