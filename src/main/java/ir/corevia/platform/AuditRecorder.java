package ir.corevia.platform;

import java.time.Instant;

public interface AuditRecorder {

    void record(String eventType, String actorId, String correlationId,
                String entityType, String entityId, String details, Instant occurredAt);
}
