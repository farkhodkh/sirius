package world.sirius.order.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;
import world.sirius.order.domain.Order;
import world.sirius.order.domain.OrderItem;
import world.sirius.order.domain.OutboxEvent;
import world.sirius.order.messaging.OutboxPublisher;
import world.sirius.order.repository.OutboxRepository;

class OutboxPublisherTest {

    OutboxRepository repo = mock(OutboxRepository.class);
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repo, rabbit, Duration.ofMillis(200));
    }

    static OutboxEvent event(String key) {
        Order order = Order.create(UUID.randomUUID(), "anna@example.com", "USD", key,
                List.of(new OrderItem("A1", "Pen", 2, new BigDecimal("1.50"))));
        return OutboxEvent.orderCreated(order, new ObjectMapper());
    }

    /** Брокер отвечает на отправку: ack=true/false, либо сначала «вернул» сообщение (нет очереди), затем ack. */
    void brokerAnswers(boolean ack, boolean returned) {
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            if (returned) {
                cd.setReturned(new ReturnedMessage(inv.getArgument(2), 312, "NO_ROUTE", "sirius.orders", "order.created"));
            }
            cd.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void confirmedEventIsSentAsPersistentJsonAndMarkedPublished() {
        OutboxEvent e = event("k1");
        when(repo.lockNextBatch()).thenReturn(List.of(e));
        brokerAnswers(true, false);

        publisher.publish();

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("sirius.orders"), eq("order.created"), sent.capture(), any(CorrelationData.class));
        MessageProperties p = sent.getValue().getMessageProperties();
        assertThat(p.getContentType()).isEqualTo("application/json");
        assertThat(p.getMessageId()).isEqualTo(e.getId().toString());
        assertThat(p.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(new String(sent.getValue().getBody())).isEqualTo(e.getPayload());   // тело - ровно JSON события, без повторного кодирования
        assertThat(e.getPublishedAt()).isNotNull();
    }

    @Test
    void rejectedEventStaysUnpublishedAndBatchStops() {
        OutboxEvent first = event("k1");
        OutboxEvent second = event("k2");
        when(repo.lockNextBatch()).thenReturn(List.of(first, second));
        brokerAnswers(false, false);

        publisher.publish();

        assertThat(first.getPublishedAt()).isNull();
        assertThat(second.getPublishedAt()).isNull();
        verify(rabbit, times(1)).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));   // вторая отправка не началась
    }

    @Test
    void unroutableMessageIsNotMarkedPublished() {
        OutboxEvent e = event("k1");
        when(repo.lockNextBatch()).thenReturn(List.of(e));
        brokerAnswers(true, true);                         // брокер принял, но очереди нет: вернул сообщение

        publisher.publish();

        assertThat(e.getPublishedAt()).isNull();
    }

    @Test
    void missingConfirmationTimesOut() {
        OutboxEvent e = event("k1");
        when(repo.lockNextBatch()).thenReturn(List.of(e));    // rabbit.send ничего не делает: ответа брокера нет

        publisher.publish();

        assertThat(e.getPublishedAt()).isNull();
    }

    @Test
    void connectionFailureLeavesEventForNextTick() {
        OutboxEvent e = event("k1");
        when(repo.lockNextBatch()).thenReturn(List.of(e));
        doAnswer(inv -> { throw new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException("refused")); })
                .when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        publisher.publish();

        assertThat(e.getPublishedAt()).isNull();
    }

    @Test
    void emptyOutboxSendsNothing() {
        when(repo.lockNextBatch()).thenReturn(List.of());

        publisher.publish();

        verify(rabbit, never()).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }
}
