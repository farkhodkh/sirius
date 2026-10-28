package world.sirius.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import world.sirius.events.OrderCreatedEvent;

@Service
public class MailNotifier {

    private final JavaMailSender mail;
    private final String from;

    public MailNotifier(JavaMailSender mail, @Value("${sirius.notification.mail-from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    /** MailException (SMTP недоступен и т.п.) - непроверяемое исключение: оно поднимется выше и запустит повтор. */
    public void sendOrderCreated(OrderCreatedEvent e) {
        mail.send(message -> {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");   // UTF-8: в теме кириллица
            helper.setFrom(from);
            helper.setTo(e.customerEmail());
            helper.setSubject("Заказ " + e.orderId() + " создан");
            helper.setText("Здравствуйте! Ваш заказ принят.\nСумма: " + e.totalAmount() + " " + e.currency());
        });
    }
}