package world.sirius.order.component;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.transaction.Transactional;
import world.sirius.order.domain.OutboxEvent;
import world.sirius.order.repository.OutboxRepository;

@Component
public class OutboxPublisher {
    private final OutboxRepository repo;
    private final RabbitTemplate rabbit;
    
    
    public OutboxPublisher() {
        this.repo = null;
        this.rabbit = new RabbitTemplate();
    }

    @Scheduled(fixedDelay = 500)               // каждые 500 мс после завершения предыдущего запуска (включите @EnableScheduling)
    @Transactional                             // блокировки строк живут до конца транзакции
    public void publish() {
        for (OutboxEvent e : repo.lockNextBatch()) {
            // exchange "sirius.orders", routing key = тип события; persistent-сообщение; messageId = id события
            rabbit.convertAndSend("sirius.orders", "order.created", e.getPayload(), m -> {
                m.getMessageProperties().setMessageId(e.getId().toString());
                m.getMessageProperties().setContentType("application/json");
                return m;
            });
            e.markPublished();                 // dirty checking сам сделает UPDATE при коммите
        }
    }
}
