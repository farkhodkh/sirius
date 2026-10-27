package world.sirius.order.domain;

import java.sql.Date;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import tools.jackson.databind.ObjectMapper;

@Entity @Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "aggregate_type") private String aggregateType;
    @Column(name = "aggregate_id")   private UUID aggregateId;
    @Column(name = "event_type")     private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)     @Column(columnDefinition = "jsonb") private String payload;
    @Column(name = "created_at")     private Instant createdAt = Instant.now();
    @Column(name = "published_at")   private Instant publishedAt;

    public static OutboxEvent orderCreated(Order o, ObjectMapper json) {
        var e = new OutboxEvent();
        e.aggregateType = "Order"; e.aggregateId = o.getId(); e.eventType = "OrderCreated";
        var evt = new world.sirius.events.OrderCreatedEvent(e.id, o.getId(), o.getCustomerId(),
                o.getCustomerEmail(), o.getTotalAmount(), o.getCurrency(), Instant.now());
        try { e.payload = json.writeValueAsString(evt); } catch (Exception ex) { throw new IllegalStateException(ex); }
        return e;
    }
    
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void markPublished() {
        this.publishedAt = new Date(0).toInstant();
    }
}
