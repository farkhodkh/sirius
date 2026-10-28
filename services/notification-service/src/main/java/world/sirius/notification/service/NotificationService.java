package world.sirius.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.sirius.events.OrderCreatedEvent;
import world.sirius.notification.repository.ProcessedEventRepository;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ProcessedEventRepository processed;
    private final MailNotifier mail;

    public NotificationService(ProcessedEventRepository processed, MailNotifier mail) {
        this.processed = processed;
        this.mail = mail;
    }

    /**
     * Один транзакционный шаг: «пометить событие обработанным» + «отправить письмо».
     * Если письмо не ушло, исключение откатывает транзакцию, метка исчезает, и повтор доставки снова всё сделает.
     *
     * @return true - письмо отправлено; false - событие уже было обработано раньше (дубликат)
     */
    @Transactional
    public boolean handle(OrderCreatedEvent event) {
        if (processed.tryMarkProcessed(event.eventId()) == 0) {
            log.info("Duplicate event {} skipped", event.eventId());
            return false;
        }
        mail.sendOrderCreated(event);
        log.info("Order {} notification sent to {}", event.orderId(), event.customerEmail());
        return true;
    }
}
