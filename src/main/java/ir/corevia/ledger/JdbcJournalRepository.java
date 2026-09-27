package ir.corevia.ledger;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
class JdbcJournalRepository implements JournalRepository {

    private final NamedParameterJdbcTemplate jdbc;

    JdbcJournalRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Journal> find(UUID id) {
        return findOne("SELECT * FROM ledger.journal WHERE id = :value", id);
    }

    @Override
    public Optional<Journal> findByIdempotencyKey(String idempotencyKey) {
        return findOne("SELECT * FROM ledger.journal WHERE idempotency_key = :value", idempotencyKey);
    }

    @Override
    public Optional<Journal> findByReversalOf(UUID originalJournalId) {
        return findOne("SELECT * FROM ledger.journal WHERE reversal_of = :value", originalJournalId);
    }

    @Override
    public void insert(Journal journal) {
        jdbc.update("""
                INSERT INTO ledger.journal
                    (id, idempotency_key, request_hash, correlation_id, business_date,
                     value_date, narrative, reversal_of, created_by, created_at)
                VALUES
                    (:id, :idempotencyKey, :requestHash, :correlationId, :businessDate,
                     :valueDate, :narrative, :reversalOf, :createdBy, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("id", journal.id())
                .addValue("idempotencyKey", journal.idempotencyKey())
                .addValue("requestHash", journal.requestHash())
                .addValue("correlationId", journal.correlationId())
                .addValue("businessDate", journal.businessDate())
                .addValue("valueDate", journal.valueDate())
                .addValue("narrative", journal.narrative())
                .addValue("reversalOf", journal.reversalOf())
                .addValue("createdBy", journal.createdBy())
                .addValue("createdAt", Timestamp.from(journal.createdAt())));

        SqlParameterSource[] batch = journal.entries().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("id", entry.id())
                        .addValue("journalId", journal.id())
                        .addValue("lineNumber", entry.lineNumber())
                        .addValue("accountId", entry.accountId())
                        .addValue("side", entry.side().name())
                        .addValue("amount", new BigDecimal(entry.amount()))
                        .addValue("currency", entry.currency())
                        .addValue("description", entry.description()))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO ledger.journal_entry
                    (id, journal_id, line_number, account_id, side, amount, currency, description)
                VALUES
                    (:id, :journalId, :lineNumber, :accountId, :side, :amount, :currency, :description)
                """, batch);
    }

    private Optional<Journal> findOne(String sql, Object value) {
        return jdbc.query(sql, Map.of("value", value), headerMapper())
                .stream()
                .findFirst()
                .map(header -> new Journal(
                        header.id(), header.idempotencyKey(), header.requestHash(), header.correlationId(),
                        header.businessDate(), header.valueDate(), header.narrative(), header.reversalOf(),
                        header.createdBy(), header.createdAt(), findEntries(header.id())));
    }

    private List<JournalEntry> findEntries(UUID journalId) {
        return jdbc.query("""
                SELECT id, line_number, account_id, side, amount, currency, description
                FROM ledger.journal_entry
                WHERE journal_id = :journalId
                ORDER BY line_number
                """, Map.of("journalId", journalId), (rs, rowNum) -> new JournalEntry(
                rs.getObject("id", UUID.class),
                rs.getInt("line_number"),
                rs.getObject("account_id", UUID.class),
                EntrySide.valueOf(rs.getString("side")),
                rs.getBigDecimal("amount").toBigIntegerExact(),
                rs.getString("currency"),
                rs.getString("description")));
    }

    private static RowMapper<JournalHeader> headerMapper() {
        return JdbcJournalRepository::mapHeader;
    }

    private static JournalHeader mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new JournalHeader(
                rs.getObject("id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getString("correlation_id"),
                rs.getObject("business_date", java.time.LocalDate.class),
                rs.getObject("value_date", java.time.LocalDate.class),
                rs.getString("narrative"),
                rs.getObject("reversal_of", UUID.class),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant());
    }

    private record JournalHeader(
            UUID id, String idempotencyKey, String requestHash, String correlationId,
            java.time.LocalDate businessDate, java.time.LocalDate valueDate, String narrative,
            UUID reversalOf, String createdBy, java.time.Instant createdAt) {
    }
}
