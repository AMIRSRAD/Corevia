package ir.corevia.ledger;

import java.time.LocalDate;
import java.util.List;

public record PostingCommand(
        String idempotencyKey,
        String correlationId,
        String actorId,
        LocalDate businessDate,
        LocalDate valueDate,
        String narrative,
        List<PostingLineCommand> entries) {

    public PostingCommand {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public String requestHash() {
        RequestHasher hasher = new RequestHasher()
                .add(idempotencyKey)
                .add(actorId)
                .add(businessDate == null ? null : businessDate.toString())
                .add(valueDate == null ? null : valueDate.toString())
                .add(narrative);
        for (PostingLineCommand entry : entries) {
            hasher.add(entry.accountId() == null ? null : entry.accountId().toString())
                    .add(entry.side() == null ? null : entry.side().name())
                    .add(entry.amount() == null ? null : entry.amount().toString())
                    .add(entry.currency())
                    .add(entry.description());
        }
        return hasher.hexDigest();
    }
}
