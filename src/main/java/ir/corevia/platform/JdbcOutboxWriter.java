package ir.corevia.platform;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcOutboxWriter implements OutboxWriter {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcOutboxWriter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(String aggregateType, String aggregateId, String eventType,
                       String payload, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO platform.outbox_event
                    (id, aggregate_type, aggregate_id, event_type, payload, occurred_at, published_at)
                VALUES
                    (:id, :aggregateType, :aggregateId, :eventType, :payload, :occurredAt, NULL)
                """, Map.of(
                "id", UUID.randomUUID(),
                "aggregateType", aggregateType,
                "aggregateId", aggregateId,
                "eventType", eventType,
                "payload", payload,
                "occurredAt", Timestamp.from(occurredAt)));
    }
}
