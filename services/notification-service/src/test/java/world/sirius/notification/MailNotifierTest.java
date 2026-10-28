package world.sirius.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessagePreparator;
import world.sirius.events.OrderCreatedEvent;
import world.sirius.notification.service.MailNotifier;

class MailNotifierTest {

    @Test
    void buildsUtf8MessageWithRecipientSubjectAndAmount() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        UUID orderId = UUID.fromString("6fa4d60c-d3c2-4de4-ab41-56994fe31d71");
        var event = new OrderCreatedEvent(UUID.randomUUID(), orderId, UUID.randomUUID(),
                "anna@example.com", new BigDecimal("3.00"), "USD", Instant.now());

        new MailNotifier(sender, "no-reply@sirius.local").sendOrderCreated(event);

        ArgumentCaptor<MimeMessagePreparator> captor = ArgumentCaptor.forClass(MimeMessagePreparator.class);
        verify(sender).send(captor.capture());
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        captor.getValue().prepare(message);
        message.saveChanges();

        assertThat(message.getFrom()[0].toString()).isEqualTo("no-reply@sirius.local");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("anna@example.com");
        assertThat(message.getSubject()).isEqualTo("Заказ 6fa4d60c-d3c2-4de4-ab41-56994fe31d71 создан");
        assertThat(message.getContent().toString()).contains("3.00 USD");
    }
}