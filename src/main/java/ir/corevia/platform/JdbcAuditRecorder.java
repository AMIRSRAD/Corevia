package ir.corevia.platform;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditRecorder implements AuditRecorder {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAuditRecorder(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String eventType, String actorId, String correlationId,
                       String entityType, String entityId, String details, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO platform.audit_record
                    (id, event_type, actor_id, correlation_id, entity_type, entity_id, details, occurred_at)
                VALUES
                    (:id, :eventType, :actorId, :correlationId, :entityType, :entityId, :details, :occurredAt)
                """, Map.of(
                "id", UUID.randomUUID(),
                "eventType", eventType,
                "actorId", actorId,
                "correlationId", correlationId,
                "entityType", entityType,
                "entityId", entityId,
                "details", details,
                "occurredAt", Timestamp.from(occurredAt)));
    }
}
