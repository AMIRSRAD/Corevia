package ir.corevia.platform;

import java.time.Instant;

public interface OutboxWriter {

    void append(String aggregateType, String aggregateId, String eventType,
                String payload, Instant occurredAt);
}
