package ir.corevia.ledger;

import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ir.corevia.platform.ActorResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journals")
class JournalController {

    private final JournalService journals;
    private final ActorResolver actors;

    JournalController(JournalService journals, ActorResolver actors) {
        this.journals = journals;
        this.actors = actors;
    }

    @PostMapping
    ResponseEntity<JournalResponse> post(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String localActorId,
            Authentication authentication,
            @Valid @RequestBody PostingRequest request) {
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        List<PostingLineCommand> entries = request.entries().stream()
                .map(line -> new PostingLineCommand(
                        line.accountId(), line.side(), new BigInteger(line.amount()),
                        line.currency(), line.description()))
                .toList();
        Journal journal = journals.post(new PostingCommand(
                idempotencyKey, correlation, actors.resolve(authentication, localActorId),
                request.businessDate(), request.valueDate(), request.narrative(), entries));
        return ResponseEntity.created(URI.create("/api/v1/journals/" + journal.id()))
                .body(JournalResponse.from(journal));
    }

    @PostMapping("/{id}/reversals")
    ResponseEntity<JournalResponse> reverse(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Actor-ID", required = false) String localActorId,
            Authentication authentication,
            @Valid @RequestBody ReversalRequest request) {
        String correlation = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId;
        Journal journal = journals.reverse(new ReversalCommand(
                id, idempotencyKey, correlation, actors.resolve(authentication, localActorId),
                request.businessDate(), request.valueDate(), request.reason()));
        return ResponseEntity.created(URI.create("/api/v1/journals/" + journal.id()))
                .body(JournalResponse.from(journal));
    }

    @GetMapping("/{id}")
    JournalResponse get(@PathVariable UUID id) {
        return JournalResponse.from(journals.get(id));
    }

    record PostingRequest(
            @NotNull LocalDate businessDate,
            @NotNull LocalDate valueDate,
            @NotBlank @Size(max = 500) String narrative,
            @NotEmpty @Size(min = 2, max = 1_000) List<@Valid PostingLineRequest> entries) {
    }

    record PostingLineRequest(
            @NotNull UUID accountId,
            @NotNull EntrySide side,
            @NotBlank @Size(max = 38) @Pattern(regexp = "[1-9][0-9]*") String amount,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Size(max = 500) String description) {
    }

    record ReversalRequest(
            @NotNull LocalDate businessDate,
            @NotNull LocalDate valueDate,
            @NotBlank @Size(max = 450) String reason) {
    }

    record JournalResponse(
            UUID id,
            String idempotencyKey,
            String correlationId,
            LocalDate businessDate,
            LocalDate valueDate,
            String narrative,
            UUID reversalOf,
            String createdBy,
            Instant createdAt,
            List<JournalEntryResponse> entries) {

        static JournalResponse from(Journal journal) {
            return new JournalResponse(
                    journal.id(), journal.idempotencyKey(), journal.correlationId(),
                    journal.businessDate(), journal.valueDate(), journal.narrative(),
                    journal.reversalOf(), journal.createdBy(), journal.createdAt(),
                    journal.entries().stream().map(JournalEntryResponse::from).toList());
        }
    }

    record JournalEntryResponse(
            UUID id,
            int lineNumber,
            UUID accountId,
            EntrySide side,
            String amount,
            String currency,
            String description) {

        static JournalEntryResponse from(JournalEntry entry) {
            return new JournalEntryResponse(
                    entry.id(), entry.lineNumber(), entry.accountId(), entry.side(),
                    entry.amount().toString(), entry.currency(), entry.description());
        }
    }
}
