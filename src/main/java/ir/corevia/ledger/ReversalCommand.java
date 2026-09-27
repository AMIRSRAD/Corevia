package ir.corevia.ledger;

import java.time.LocalDate;
import java.util.UUID;

public record ReversalCommand(
        UUID originalJournalId,
        String idempotencyKey,
        String correlationId,
        String actorId,
        LocalDate businessDate,
        LocalDate valueDate,
        String reason) {

    public String requestHash() {
        return new RequestHasher()
                .add(originalJournalId == null ? null : originalJournalId.toString())
                .add(idempotencyKey)
                .add(actorId)
                .add(businessDate == null ? null : businessDate.toString())
                .add(valueDate == null ? null : valueDate.toString())
                .add(reason)
                .hexDigest();
    }
}
