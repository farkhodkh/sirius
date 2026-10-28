package world.sirius.notification.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import world.sirius.notification.domain.ProcessedEvent;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Атомарно «занимает» событие: вернёт 1, если мы первые, и 0, если оно уже обработано.
     * Проверка и запись - одна команда SQL, поэтому два потребителя не обработают одно событие дважды.
     */
    @Modifying
    @Query(value = "INSERT INTO app.processed_events(event_id, processed_at) VALUES (:id, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int tryMarkProcessed(@Param("id") UUID id);
}
