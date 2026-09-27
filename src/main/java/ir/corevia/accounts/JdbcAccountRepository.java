package ir.corevia.accounts;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountStore, AccountPostingPort {

    private static final RowMapper<Account> ACCOUNT_MAPPER = JdbcAccountRepository::mapAccount;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Account account) {
        jdbc.update("""
                INSERT INTO account.bank_account
                    (id, account_number, name, currency, normal_balance, status,
                     debit_total, credit_total, version, created_at)
                VALUES
                    (:id, :accountNumber, :name, :currency, :normalBalance, :status,
                     :debitTotal, :creditTotal, :version, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", account.id())
                .addValue("accountNumber", account.accountNumber())
                .addValue("name", account.name())
                .addValue("currency", account.currency())
                .addValue("normalBalance", account.normalBalance().name())
                .addValue("status", account.status().name())
                .addValue("debitTotal", new BigDecimal(account.debitTotal()))
                .addValue("creditTotal", new BigDecimal(account.creditTotal()))
                .addValue("version", account.version())
                .addValue("createdAt", Timestamp.from(account.createdAt())));
    }

    @Override
    public Optional<Account> find(UUID id) {
        return jdbc.query("SELECT * FROM account.bank_account WHERE id = :id",
                        Map.of("id", id), ACCOUNT_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Map<UUID, PostingAccount> lockForPosting(List<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        List<PostingAccount> rows = jdbc.query("""
                        SELECT id, currency, normal_balance, status, version
                        FROM account.bank_account
                        WHERE id IN (:ids)
                        ORDER BY id
                        FOR UPDATE
                        """, Map.of("ids", accountIds), (rs, rowNum) -> new PostingAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("currency"),
                        NormalBalance.valueOf(rs.getString("normal_balance")),
                        AccountStatus.valueOf(rs.getString("status")),
                        rs.getLong("version")));
        Map<UUID, PostingAccount> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.id(), row));
        return result;
    }

    @Override
    public void apply(UUID accountId, BigInteger debitDelta, BigInteger creditDelta, long expectedVersion) {
        int updated = jdbc.update("""
                UPDATE account.bank_account
                SET debit_total = debit_total + :debitDelta,
                    credit_total = credit_total + :creditDelta,
                    version = version + 1
                WHERE id = :id AND version = :expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", accountId)
                .addValue("debitDelta", new BigDecimal(debitDelta))
                .addValue("creditDelta", new BigDecimal(creditDelta))
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new AccountException("Concurrent account update detected: " + accountId);
        }
    }

    private static Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new Account(
                rs.getObject("id", UUID.class),
                rs.getString("account_number"),
                rs.getString("name"),
                rs.getString("currency"),
                NormalBalance.valueOf(rs.getString("normal_balance")),
                AccountStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("debit_total").toBigIntegerExact(),
                rs.getBigDecimal("credit_total").toBigIntegerExact(),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant());
    }
}
