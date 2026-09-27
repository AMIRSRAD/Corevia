package ir.corevia.ledger;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import ir.corevia.accounts.AccountPostingPort;
import ir.corevia.accounts.AccountStatus;
import ir.corevia.accounts.PostingAccount;
import ir.corevia.platform.AuditRecorder;
import ir.corevia.platform.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalService {

    private final JournalRepository journals;
    private final AccountPostingPort accounts;
    private final AuditRecorder audit;
    private final OutboxWriter outbox;
    private final Clock clock;

    public JournalService(JournalRepository journals, AccountPostingPort accounts,
                          AuditRecorder audit, OutboxWriter outbox) {
        this.journals = journals;
        this.accounts = accounts;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public Journal post(PostingCommand command) {
        validateEnvelope(command.idempotencyKey(), command.correlationId(), command.actorId());
        String hash = command.requestHash();
        OptionalResult replay = replay(command.idempotencyKey(), hash);
        if (replay.journal() != null) {
            return replay.journal();
        }
        return commit(command, hash, null, "JournalPosted");
    }

    @Transactional
    public Journal reverse(ReversalCommand command) {
        validateEnvelope(command.idempotencyKey(), command.correlationId(), command.actorId());
        if (command.originalJournalId() == null) {
            throw new InvalidPostingException("originalJournalId is required");
        }
        String hash = command.requestHash();
        OptionalResult replay = replay(command.idempotencyKey(), hash);
        if (replay.journal() != null) {
            return replay.journal();
        }

        Journal original = journals.find(command.originalJournalId())
                .orElseThrow(() -> new JournalNotFoundException(command.originalJournalId()));
        if (original.reversalOf() != null) {
            throw new InvalidPostingException("A reversal journal cannot itself be reversed");
        }
        if (journals.findByReversalOf(original.id()).isPresent()) {
            throw new JournalAlreadyReversedException(original.id());
        }

        String reason = required(command.reason(), "reason", 450);
        List<PostingLineCommand> reversedEntries = original.entries().stream()
                .map(entry -> new PostingLineCommand(
                        entry.accountId(), entry.side().opposite(), entry.amount(),
                        entry.currency(), "Reversal of line " + entry.lineNumber()))
                .toList();
        PostingCommand posting = new PostingCommand(
                command.idempotencyKey(), command.correlationId(), command.actorId(),
                command.businessDate(), command.valueDate(), "REVERSAL: " + reason, reversedEntries);
        return commit(posting, hash, original.id(), "JournalReversed");
    }

    @Transactional(readOnly = true)
    public Journal get(UUID id) {
        return journals.find(id).orElseThrow(() -> new JournalNotFoundException(id));
    }

    private Journal commit(PostingCommand command, String requestHash, UUID reversalOf, String eventType) {
        ValidatedPosting validated = validatePosting(command);
        Map<UUID, PostingAccount> locked = accounts.lockForPosting(validated.accountIds());
        if (locked.size() != validated.accountIds().size()) {
            List<UUID> missing = validated.accountIds().stream().filter(id -> !locked.containsKey(id)).toList();
            throw new InvalidPostingException("Unknown account(s): " + missing);
        }

        Map<UUID, BalanceDelta> deltas = new LinkedHashMap<>();
        for (ValidatedLine line : validated.lines()) {
            PostingAccount account = locked.get(line.accountId());
            if (account.status() != AccountStatus.OPEN) {
                throw new InvalidPostingException("Account is not open for posting: " + account.id());
            }
            if (!account.currency().equals(line.currency())) {
                throw new InvalidPostingException("Entry currency does not match account currency: " + account.id());
            }
            deltas.computeIfAbsent(account.id(), ignored -> new BalanceDelta())
                    .add(line.side(), line.amount());
        }

        // PostgreSQL stores timestamp values at microsecond precision. Normalize before
        // returning so an idempotent replay serializes identically to the first response.
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        UUID journalId = UUID.randomUUID();
        List<JournalEntry> entries = new ArrayList<>(validated.lines().size());
        for (int index = 0; index < validated.lines().size(); index++) {
            ValidatedLine line = validated.lines().get(index);
            entries.add(new JournalEntry(
                    UUID.randomUUID(), index + 1, line.accountId(), line.side(), line.amount(),
                    line.currency(), line.description()));
        }
        Journal journal = new Journal(
                journalId, command.idempotencyKey().strip(), requestHash,
                command.correlationId().strip(), command.businessDate(), command.valueDate(),
                required(command.narrative(), "narrative", 500), reversalOf,
                command.actorId().strip(), now, entries);

        journals.insert(journal);
        for (UUID accountId : validated.accountIds()) {
            BalanceDelta delta = deltas.get(accountId);
            accounts.apply(accountId, delta.debits, delta.credits, locked.get(accountId).version());
        }

        String payload = "{\"journalId\":\"" + journal.id() + "\",\"eventType\":\"" + eventType + "\"}";
        outbox.append("Journal", journal.id().toString(), "ledger." + eventType, payload, now);
        audit.record(eventType, journal.createdBy(), journal.correlationId(),
                "Journal", journal.id().toString(), payload, now);
        return journal;
    }

    private ValidatedPosting validatePosting(PostingCommand command) {
        if (command.businessDate() == null || command.valueDate() == null) {
            throw new InvalidPostingException("businessDate and valueDate are required");
        }
        if (command.entries().size() < 2) {
            throw new InvalidPostingException("A journal requires at least two entries");
        }

        List<ValidatedLine> lines = new ArrayList<>();
        Map<String, CurrencyTotals> totals = new HashMap<>();
        for (PostingLineCommand entry : command.entries()) {
            if (entry.accountId() == null || entry.side() == null || entry.amount() == null) {
                throw new InvalidPostingException("Every entry requires accountId, side, and amount");
            }
            if (entry.amount().signum() <= 0) {
                throw new InvalidPostingException("Entry amounts must be positive integers");
            }
            if (entry.amount().toString().length() > 38) {
                throw new InvalidPostingException("Entry amounts cannot exceed 38 digits");
            }
            String currency = required(entry.currency(), "entry.currency", 3).toUpperCase(Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) {
                throw new InvalidPostingException("Entry currency must be a three-letter code");
            }
            String description = entry.description() == null ? "" : entry.description().strip();
            if (description.length() > 500) {
                throw new InvalidPostingException("Entry description exceeds 500 characters");
            }
            lines.add(new ValidatedLine(entry.accountId(), entry.side(), entry.amount(), currency, description));
            totals.computeIfAbsent(currency, ignored -> new CurrencyTotals()).add(entry.side(), entry.amount());
        }
        totals.forEach((currency, value) -> {
            if (!value.debits.equals(value.credits)) {
                throw new InvalidPostingException("Journal is not balanced for " + currency
                        + ": debits=" + value.debits + ", credits=" + value.credits);
            }
        });

        List<UUID> accountIds = lines.stream().map(ValidatedLine::accountId).distinct()
                .sorted(Comparator.naturalOrder()).toList();
        return new ValidatedPosting(lines, accountIds);
    }

    private OptionalResult replay(String idempotencyKey, String requestHash) {
        Journal existing = journals.findByIdempotencyKey(idempotencyKey.strip()).orElse(null);
        if (existing == null) {
            return new OptionalResult(null);
        }
        if (!existing.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        return new OptionalResult(existing);
    }

    private static void validateEnvelope(String idempotencyKey, String correlationId, String actorId) {
        required(idempotencyKey, "idempotencyKey", 128);
        required(correlationId, "correlationId", 128);
        required(actorId, "actorId", 200);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidPostingException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new InvalidPostingException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private record ValidatedLine(
            UUID accountId, EntrySide side, BigInteger amount, String currency, String description) {
    }

    private record ValidatedPosting(List<ValidatedLine> lines, List<UUID> accountIds) {
    }

    private record OptionalResult(Journal journal) {
    }

    private static final class CurrencyTotals {
        private BigInteger debits = BigInteger.ZERO;
        private BigInteger credits = BigInteger.ZERO;

        void add(EntrySide side, BigInteger amount) {
            if (side == EntrySide.DEBIT) {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }
    }

    private static final class BalanceDelta {
        private BigInteger debits = BigInteger.ZERO;
        private BigInteger credits = BigInteger.ZERO;

        void add(EntrySide side, BigInteger amount) {
            if (side == EntrySide.DEBIT) {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }
    }
}
