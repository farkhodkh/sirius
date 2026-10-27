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

import java.util.Optional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orders, OutboxRepository outbox,
            ObjectMapper json, TransactionTemplate tx) {
        this.orders = orders;
        this.outbox = outbox;
        this.json = json;
        this.tx = tx;
    }

    @Transactional(readOnly = true)
    public Page<Order> list(UUID customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Order> get(UUID id, UUID customerId) {
        return orders.findWithItems(id, customerId);
    }

    /**
     * Создание заказа идемпотентно по (customerId, idempotencyKey).
     * Метод намеренно БЕЗ @Transactional: транзакциями управляем вручную через
     * TransactionTemplate,
     * потому что после нарушения UNIQUE транзакция Postgres уже «aborted» и в ней
     * нельзя читать.
     */
    public Order create(UUID customerId, String email, CreateOrderCommand cmd, String idemKey) {
        try {
            return tx.execute(status -> createInTx(customerId, email, cmd, idemKey));
        } catch (DataIntegrityViolationException race) {
            // два одинаковых запроса пришли одновременно, этот проиграл гонку: читаем
            // победителя в НОВОЙ транзакции
            return tx.execute(status -> orders.findByCustomerIdAndIdempotencyKey(customerId, idemKey))
                    .orElseThrow(() -> race);
        }
    }

    private Order createInTx(UUID customerId, String email, CreateOrderCommand cmd, String idemKey) {
        // 1) повтор запроса (мобильная сеть, клиент ретраит) возвращает тот же заказ
        Optional<Order> existing = orders.findByCustomerIdAndIdempotencyKey(customerId, idemKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        Order order = Order.create(customerId, email, cmd.currency(), idemKey, cmd.toItems());
        orders.saveAndFlush(order); // flush: нарушение UNIQUE обнаружится здесь
        outbox.save(OutboxEvent.orderCreated(order, json)); // событие пишется в ТОЙ ЖЕ транзакции (outbox)
        return order;
    }
}