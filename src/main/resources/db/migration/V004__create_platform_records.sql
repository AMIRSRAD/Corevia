CREATE TABLE platform.audit_record (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    actor_id VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    details TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_audit_record_entity ON platform.audit_record (entity_type, entity_id);
CREATE INDEX ix_audit_record_occurred_at ON platform.audit_record (occurred_at);

CREATE TABLE platform.outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(150) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX ix_outbox_event_unpublished ON platform.outbox_event (published_at, occurred_at);
