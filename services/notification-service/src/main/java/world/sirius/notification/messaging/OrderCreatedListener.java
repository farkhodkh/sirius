package world.sirius.notification.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import world.sirius.events.OrderCreatedEvent;
import world.sirius.notification.service.NotificationService;

@Component
public class OrderCreatedListener {

    private final NotificationService notifications;

    public OrderCreatedListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    /**
     * JSON из очереди превращается в OrderCreatedEvent конвертером из MessagingConfig.
     * Исключение из метода запускает повторы (см. spring.rabbitmq.listener.simple.retry), а после них - уход в DLQ.
     * Сообщение, которое не удаётся разобрать (не тот JSON), повторять бессмысленно: оно тоже закончит в DLQ.
     */
    @RabbitListener(queues = RabbitTopology.QUEUE)
    public void on(OrderCreatedEvent event) {
        notifications.handle(event);
    }
}
