package ir.corevia.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ir.corevia.accounts.Account;
import ir.corevia.accounts.AccountService;
import ir.corevia.accounts.CreateAccountCommand;
import ir.corevia.accounts.NormalBalance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentPostingIntegrationTest {

    @Autowired
    private JournalService journals;

    @Autowired
    private AccountService accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentPostingsOnTheSameAccountsDoNotLoseUpdates() throws Exception {
        Account debit = account("CONCURRENT-D", NormalBalance.DEBIT);
        Account credit = account("CONCURRENT-C", NormalBalance.CREDIT);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Journal> first = executor.submit(() -> {
                start.await();
                return journals.post(posting("concurrent-1", debit.id(), credit.id(), "100"));
            });
            Future<Journal> second = executor.submit(() -> {
                start.await();
                return journals.post(posting("concurrent-2", debit.id(), credit.id(), "200"));
            });
            start.countDown();

            assertThat(first.get().id()).isNotEqualTo(second.get().id());
        }

        assertThat(accounts.get(debit.id()).bookBalance()).isEqualTo(new BigInteger("300"));
        assertThat(accounts.get(credit.id()).bookBalance()).isEqualTo(new BigInteger("300"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger.journal", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform.outbox_event", Long.class)).isEqualTo(2);
    }

    private Account account(String prefix, NormalBalance side) {
        return accounts.create(new CreateAccountCommand(
                prefix + "-" + UUID.randomUUID(), prefix, "IRR", side));
    }

    private static PostingCommand posting(String key, UUID debit, UUID credit, String amount) {
        LocalDate date = LocalDate.of(2026, 7, 12);
        return new PostingCommand(key, "corr-" + key, "concurrency-test", date, date, key, List.of(
                new PostingLineCommand(debit, EntrySide.DEBIT, new BigInteger(amount), "IRR", "debit"),
                new PostingLineCommand(credit, EntrySide.CREDIT, new BigInteger(amount), "IRR", "credit")));
    }
}
