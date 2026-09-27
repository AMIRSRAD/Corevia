package ir.corevia.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record Journal(
        UUID id,
        String idempotencyKey,
        String requestHash,
        String correlationId,
        LocalDate businessDate,
        LocalDate valueDate,
        String narrative,
        UUID reversalOf,
        String createdBy,
        Instant createdAt,
        List<JournalEntry> entries) {

    public Journal {
        entries = List.copyOf(entries);
    }
}
