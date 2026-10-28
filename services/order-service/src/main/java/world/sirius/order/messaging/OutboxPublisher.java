package world.sirius.order.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import world.sirius.order.domain.OutboxEvent;
import world.sirius.order.repository.OutboxRepository;

/**
 * Берёт неопубликованные события из outbox и отправляет в RabbitMQ.
 * Событие считается опубликованным ТОЛЬКО после подтверждения брокера (publisher confirm) и только если оно
 * дошло хотя бы до одной очереди (иначе брокер вернёт его как «unroutable»).
 * Включается свойством sirius.outbox.publisher.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "sirius.outbox.publisher.enabled", havingValue = "true")
public class OutboxPublisher {

    static final String EXCHANGE = "sirius.orders";
    static final String ROUTING_KEY = "order.created";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repo;
    private final RabbitTemplate rabbit;
    private final Duration confirmTimeout;

    public OutboxPublisher(OutboxRepository repo, RabbitTemplate rabbit,
                           @Value("${sirius.outbox.publisher.confirm-timeout:5s}") Duration confirmTimeout) {
        this.repo = repo;
        this.rabbit = rabbit;
        this.confirmTimeout = confirmTimeout;
    }

    @Scheduled(fixedDelay = 500)
    @Transactional                                         // блокировки строк (SKIP LOCKED) живут до конца транзакции
    public void publish() {
        for (OutboxEvent event : repo.lockNextBatch()) {
            if (!send(event)) {
                break;                                     // порядок сохраняем: после неудачи пачку не продолжаем, повтор на следующем тике
            }
            event.markPublished();                         // dirty checking сделает UPDATE при коммите
        }
    }

    /** @return true, если брокер принял сообщение и оно попало в очередь */
    private boolean send(OutboxEvent event) {
        CorrelationData correlation = new CorrelationData(event.getId().toString());
        Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.getId().toString())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)        // сообщение сохраняется на диск брокера
                .build();
        try {
            rabbit.send(EXCHANGE, ROUTING_KEY, message, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                log.warn("Broker rejected outbox event {}: {}", event.getId(), confirm.getReason());
                return false;
            }
            if (correlation.getReturned() != null) {
                log.warn("Outbox event {} is unroutable (no queue is bound to {}/{}); start notification-service once to create the queue",
                        event.getId(), EXCHANGE, ROUTING_KEY);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            log.warn("Outbox event {} was not confirmed by the broker: {}", event.getId(), e.toString());
            return false;
        }
    }
}