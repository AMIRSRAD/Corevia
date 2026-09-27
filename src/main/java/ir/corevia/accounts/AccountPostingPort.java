package ir.corevia.accounts;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AccountPostingPort {

    Map<UUID, PostingAccount> lockForPosting(List<UUID> accountIds);

    void apply(UUID accountId, BigInteger debitDelta, BigInteger creditDelta, long expectedVersion);
}
