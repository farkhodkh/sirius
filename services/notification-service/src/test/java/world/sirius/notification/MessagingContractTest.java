package world.sirius.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.ObjectMapper;
import world.sirius.events.OrderCreatedEvent;
import world.sirius.notification.messaging.RabbitTopology;

@SpringBootTest
@ActiveProfiles("test")
class MessagingContractTest {

    @Autowired ApplicationContext context;
    @Autowired JacksonJsonMessageConverter converter;
    @Autowired RabbitListenerEndpointRegistry registry;
    @Autowired RabbitProperties rabbit;

    @Test
    void jsonWrittenByOrderServiceIsReadAsEvent() {
        // так order-service формирует тело: ObjectMapper Jackson 3 -> строка JSON -> байты с content-type application/json
        var sent = new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "anna@example.com", new BigDecimal("3.00"), "USD", Instant.parse("2026-10-27T17:14:13.707171855Z"));
        String payload = new ObjectMapper().writeValueAsString(sent);
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build();

        OrderCreatedEvent received = (OrderCreatedEvent) converter.fromMessage(message,
                new ParameterizedTypeReference<OrderCreatedEvent>() { });

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void topologyHasMainQueueWithDeadLettering() {
        Queue queue = context.getBean("orderCreatedQueue", Queue.class);
        Queue dlq = context.getBean("orderCreatedDlq", Queue.class);

        assertThat(queue.getName()).isEqualTo("notification.order-created");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", "sirius.dlx")
                .containsEntry("x-dead-letter-routing-key", "notification.order-created.dlq");
        assertThat(dlq.getName()).isEqualTo("notification.order-created.dlq");
        assertThat(context.getBean(TopicExchange.class).getName()).isEqualTo("sirius.orders");
        assertThat(context.getBean(DirectExchange.class).getName()).isEqualTo("sirius.dlx");

        List<Binding> bindings = List.copyOf(context.getBeansOfType(Binding.class).values());
        assertThat(bindings).extracting(Binding::getRoutingKey)
                .containsExactlyInAnyOrder("order.created", "notification.order-created.dlq");
    }

    @Test
    void listenerIsRegisteredOnTheQueue() {
        assertThat(registry.getListenerContainers()).hasSize(1);
        MessageListenerContainer container = registry.getListenerContainers().iterator().next();
        assertThat(((AbstractMessageListenerContainer) container).getQueueNames())
                .containsExactly(RabbitTopology.QUEUE);
    }

    /** Читает настройки из БОЕВОГО application.yml (тестовый профиль лишь отключает подключение к брокеру). */
    @Test
    void retrySettingsFromApplicationYmlAreBound() {
        var simple = rabbit.getListener().getSimple();
        assertThat(simple.getRetry().isEnabled()).isTrue();
        assertThat(simple.getRetry().getMaxRetries()).isEqualTo(3);
        assertThat(simple.getRetry().getMultiplier()).isEqualTo(2.0);
        assertThat(simple.getRetry().getInitialInterval()).isEqualTo(java.time.Duration.ofSeconds(1));
        assertThat(simple.getDefaultRequeueRejected()).isFalse();
        assertThat(simple.getAcknowledgeMode().name()).isEqualTo("AUTO");
        assertThat(simple.getPrefetch()).isEqualTo(10);
    }
}