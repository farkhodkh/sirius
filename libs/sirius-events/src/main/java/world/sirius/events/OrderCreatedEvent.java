package world.sirius.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId, UUID orderId, UUID customerId, String customerEmail,
        BigDecimal totalAmount, String currency, Instant occurredAt) {}
