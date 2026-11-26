package com.socialpilot.app.domain.contract.ai;

import java.util.List;

/**
 * Durable usage history shared by every AI consumer. The production implementation is the
 * SP-029 ledger. Implementations do I/O and are called from workers.
 */
public interface UsageLog {
    /**
     * Idempotent per requestId: repeating the same terminal record keeps the first one, so
     * duplicate terminal callbacks never double count.
     *
     * @return true when the record was stored for the first time
     */
    boolean record(UsageRecord record);

    /** Records whose pinned month is {@code monthKey}, any zone. */
    List<UsageRecord> forMonth(String monthKey);
}
