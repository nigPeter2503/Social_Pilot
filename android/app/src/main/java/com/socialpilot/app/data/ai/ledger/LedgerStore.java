package com.socialpilot.app.data.ai.ledger;

import java.util.List;
import java.util.concurrent.Callable;

/** Storage operations the ledger needs; every read-then-write runs inside {@link #inTransaction}. */
public interface LedgerStore {
    <T> T inTransaction(Callable<T> work);

    AttemptEntity find(String requestId);

    void insert(AttemptEntity row);

    void update(AttemptEntity row);

    /** Releases known-undispatched queued attempts in the transaction that stops dispatch. */
    void releaseQueued(long now);

    /** Settled estimates plus held reservations; RELEASED counts zero. */
    long committedForDay(String dayKey, String currency);

    long committedForMonth(String monthKey, String currency);

    int uncertainForMonth(String monthKey);

    BudgetSettingsEntity settings();

    void saveSettings(BudgetSettingsEntity settings);

    /** RESERVED rows of other processes become UNCERTAIN; returns how many. */
    int markInterrupted(String currentBootId, long now);

    /** Drops channel/session identity of a channel's attempts; cost, state and periods stay. */
    int forgetChannel(String channelId, long retiredRevision, long now);

    List<AttemptEntity> forMonth(String monthKey);
}
