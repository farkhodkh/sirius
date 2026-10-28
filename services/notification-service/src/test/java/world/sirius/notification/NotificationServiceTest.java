package world.sirius.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import world.sirius.events.OrderCreatedEvent;
import world.sirius.notification.repository.ProcessedEventRepository;
import world.sirius.notification.service.MailNotifier;
import world.sirius.notification.service.NotificationService;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired NotificationService service;
    @Autowired ProcessedEventRepository processed;
    @MockitoBean MailNotifier mail;

    static OrderCreatedEvent event() {
        return new OrderCreatedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "anna@example.com", new BigDecimal("3.00"), "USD", Instant.now());
    }

    @BeforeEach
    void clean() {
        processed.deleteAll();
    }

    @Test
    void firstDeliverySendsMail() {
        OrderCreatedEvent e = event();

        assertThat(service.handle(e)).isTrue();

        verify(mail).sendOrderCreated(e);
        assertThat(processed.existsById(e.eventId())).isTrue();
    }

    @Test
    void duplicateDeliveryIsSkipped() {
        OrderCreatedEvent e = event();

        assertThat(service.handle(e)).isTrue();
        assertThat(service.handle(e)).isFalse();          // та же доставка пришла повторно

        verify(mail, times(1)).sendOrderCreated(e);        // письмо ушло один раз
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void mailFailureRollsBackAndRetryStillSends() {
        OrderCreatedEvent e = event();
        doThrow(new MailSendException("smtp down")).when(mail).sendOrderCreated(e);

        assertThatThrownBy(() -> service.handle(e)).isInstanceOf(MailSendException.class);
        assertThat(processed.existsById(e.eventId())).isFalse();      // метка откатилась вместе с транзакцией

        doNothing().when(mail).sendOrderCreated(any());               // SMTP «ожил»: повтор доставки
        assertThat(service.handle(e)).isTrue();
        assertThat(processed.existsById(e.eventId())).isTrue();
    }
}