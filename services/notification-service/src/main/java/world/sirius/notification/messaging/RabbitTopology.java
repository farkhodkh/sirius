package world.sirius.notification.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Топология RabbitMQ. Spring (RabbitAdmin) создаёт всё это в брокере при первом подключении, повторно безопасно.
 * Обменник "sirius.orders" объявляется и в order-service (с теми же параметрами).
 */
@Configuration
public class RabbitTopology {

    public static final String ORDERS_EXCHANGE = "sirius.orders";
    public static final String DLX = "sirius.dlx";
    public static final String QUEUE = "notification.order-created";
    public static final String DLQ = QUEUE + ".dlq";
    public static final String ROUTING_KEY = "order.created";

    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE, true, false);      // durable, не auto-delete
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue orderCreatedQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)             // куда уходит сообщение, отвергнутое после всех повторов
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    Queue orderCreatedDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(ordersExchange).with(ROUTING_KEY);
    }

    @Bean
    Binding dlqBinding(Queue orderCreatedDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(orderCreatedDlq).to(deadLetterExchange).with(DLQ);
    }
}