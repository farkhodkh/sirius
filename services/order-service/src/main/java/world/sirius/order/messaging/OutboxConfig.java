package world.sirius.order.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включается вместе с публикатором: запуск @Scheduled и объявление обменника в брокере. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "sirius.outbox.publisher.enabled", havingValue = "true")
public class OutboxConfig {

    /** Параметры должны совпадать с объявлением в notification-service, иначе брокер откажет (PRECONDITION_FAILED). */
    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(OutboxPublisher.EXCHANGE, true, false);
    }
}