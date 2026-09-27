package ir.corevia.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ir.corevia.accounts.Account;
import ir.corevia.accounts.AccountService;
import ir.corevia.accounts.CreateAccountCommand;
import ir.corevia.accounts.NormalBalance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JournalServiceIntegrationTest {

    @Autowired
    private JournalService journals;

    @Autowired
    private AccountService accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void balancedPostingIsAtomicAndIdempotent() {
        Account cash = account("CASH", NormalBalance.DEBIT);
        Account customer = account("CUSTOMER", NormalBalance.CREDIT);
        PostingCommand command = posting("posting-1", "Initial funding", cash.id(), customer.id(), "1000000");

        Journal posted = journals.post(command);
        Journal replayed = journals.post(command);

        assertThat(replayed.id()).isEqualTo(posted.id());
        assertThat(journals.get(posted.id()).entries()).hasSize(2);
        assertThat(accounts.get(cash.id()).bookBalance()).isEqualTo(new BigInteger("1000000"));
        assertThat(accounts.get(customer.id()).bookBalance()).isEqualTo(new BigInteger("1000000"));
        assertThat(count("ledger.journal")).isEqualTo(1);
        assertThat(count("ledger.journal_entry")).isEqualTo(2);
        assertThat(count("platform.audit_record")).isEqualTo(1);
        assertThat(count("platform.outbox_event")).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyCannotRepresentDifferentWork() {
        Account cash = account("CASH-IDEM", NormalBalance.DEBIT);
        Account customer = account("CUSTOMER-IDEM", NormalBalance.CREDIT);
        journals.post(posting("same-key", "First", cash.id(), customer.id(), "10"));

        assertThatThrownBy(() -> journals.post(
                posting("same-key", "Different", cash.id(), customer.id(), "10")))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(count("ledger.journal")).isEqualTo(1);
    }

    @Test
    void unbalancedJournalDoesNotChangeFinancialState() {
        Account cash = account("CASH-UNBAL", NormalBalance.DEBIT);
        Account customer = account("CUSTOMER-UNBAL", NormalBalance.CREDIT);
        PostingCommand unbalanced = new PostingCommand(
                "unbalanced", "corr-unbalanced", "tester", LocalDate.of(2026, 3, 21),
                LocalDate.of(2026, 3, 21), "Must fail", List.of(
                new PostingLineCommand(cash.id(), EntrySide.DEBIT, BigInteger.TEN, "IRR", "debit"),
                new PostingLineCommand(customer.id(), EntrySide.CREDIT, BigInteger.ONE, "IRR", "credit")));

        assertThatThrownBy(() -> journals.post(unbalanced))
                .isInstanceOf(InvalidPostingException.class)
                .hasMessageContaining("not balanced");
        assertThat(accounts.get(cash.id()).bookBalance()).isZero();
        assertThat(accounts.get(customer.id()).bookBalance()).isZero();
        assertThat(count("ledger.journal")).isZero();
    }

    @Test
    void reversalCreatesOppositeJournalAndRestoresBalances() {
        Account cash = account("CASH-REV", NormalBalance.DEBIT);
        Account customer = account("CUSTOMER-REV", NormalBalance.CREDIT);
        Journal original = journals.post(posting("original", "Funding", cash.id(), customer.id(), "500"));

        ReversalCommand command = new ReversalCommand(
                original.id(), "reverse-1", "corr-reverse", "reviewer",
                LocalDate.of(2026, 3, 22), LocalDate.of(2026, 3, 22), "Operator correction");
        Journal reversal = journals.reverse(command);
        Journal replay = journals.reverse(command);

        assertThat(replay.id()).isEqualTo(reversal.id());
        assertThat(reversal.reversalOf()).isEqualTo(original.id());
        assertThat(reversal.entries()).extracting(JournalEntry::side)
                .containsExactly(EntrySide.CREDIT, EntrySide.DEBIT);
        assertThat(accounts.get(cash.id()).bookBalance()).isZero();
        assertThat(accounts.get(customer.id()).bookBalance()).isZero();
        assertThat(count("ledger.journal")).isEqualTo(2);
        assertThat(count("platform.audit_record")).isEqualTo(2);
        assertThat(count("platform.outbox_event")).isEqualTo(2);

        assertThatThrownBy(() -> journals.reverse(new ReversalCommand(
                original.id(), "reverse-2", "corr-reverse-2", "reviewer",
                LocalDate.of(2026, 3, 22), LocalDate.of(2026, 3, 22), "Second reversal")))
                .isInstanceOf(JournalAlreadyReversedException.class);
    }

    @Test
    void entryCurrencyMustMatchTheAccount() {
        Account usd = accounts.create(new CreateAccountCommand(
                unique("USD"), "USD asset", "USD", NormalBalance.DEBIT));
        Account irr = account("IRR", NormalBalance.CREDIT);

        assertThatThrownBy(() -> journals.post(posting(
                "wrong-currency", "Wrong", usd.id(), irr.id(), "100")))
                .isInstanceOf(InvalidPostingException.class)
                .hasMessageContaining("does not match account currency");
        assertThat(count("ledger.journal")).isZero();
    }

    @Test
    void amountPrecisionIsRejectedBeforeDatabaseWrite() {
        Account cash = account("CASH-PRECISION", NormalBalance.DEBIT);
        Account customer = account("CUSTOMER-PRECISION", NormalBalance.CREDIT);
        String thirtyNineDigits = "1".repeat(39);

        assertThatThrownBy(() -> journals.post(posting(
                "too-large", "Too large", cash.id(), customer.id(), thirtyNineDigits)))
                .isInstanceOf(InvalidPostingException.class)
                .hasMessageContaining("38 digits");
        assertThat(count("ledger.journal")).isZero();
    }

    private Account account(String prefix, NormalBalance normalBalance) {
        return accounts.create(new CreateAccountCommand(unique(prefix), prefix, "IRR", normalBalance));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static PostingCommand posting(
            String key, String narrative, UUID debitAccount, UUID creditAccount, String amount) {
        LocalDate date = LocalDate.of(2026, 3, 21);
        return new PostingCommand(
                key, "corr-" + key, "tester", date, date, narrative, List.of(
                new PostingLineCommand(debitAccount, EntrySide.DEBIT, new BigInteger(amount), "IRR", "debit"),
                new PostingLineCommand(creditAccount, EntrySide.CREDIT, new BigInteger(amount), "IRR", "credit")));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
