package com.socialpilot.app.data.ai.ledger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Labelled JVM fake with the same rules as the Room store: one writer at a time (like SQLite's
 * write lock) and copies in and out, so callers cannot mutate stored rows without update().
 * The real store is checked by LedgerDatabaseDeviceTest.
 */
final class MemoryLedgerStore implements LedgerStore {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, AttemptEntity> rows = new LinkedHashMap<>();
    private BudgetSettingsEntity settings;

    @Override public <T> T inTransaction(Callable<T> work) {
        lock.lock();
        try {
            return work.call();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        } finally {
            lock.unlock();
        }
    }

    private void requireTransaction() {
        if (!lock.isHeldByCurrentThread()) throw new IllegalStateException("Outside transaction");
    }

    @Override public AttemptEntity find(String requestId) {
        requireTransaction();
        AttemptEntity row = rows.get(requestId);
        return row == null ? null : copy(row);
    }

    @Override public void insert(AttemptEntity row) {
        requireTransaction();
        if (rows.containsKey(row.requestId)) throw new IllegalStateException("UNIQUE constraint failed");
        rows.put(row.requestId, copy(row));
    }

    @Override public void update(AttemptEntity row) {
        requireTransaction();
        if (rows.containsKey(row.requestId)) rows.put(row.requestId, copy(row));
    }

    @Override public long committedForDay(String dayKey, String currency) {
        requireTransaction();
        long sum = 0;
        for (AttemptEntity row : rows.values()) if (row.dayKey.equals(dayKey) && row.currency.equals(currency)) sum += committed(row);
        return sum;
    }

    @Override public long committedForMonth(String monthKey, String currency) {
        requireTransaction();
        long sum = 0;
        for (AttemptEntity row : rows.values()) if (row.monthKey.equals(monthKey) && row.currency.equals(currency)) sum += committed(row);
        return sum;
    }

    private static long committed(AttemptEntity row) {
        if (AttemptEntity.SETTLED.equals(row.state)) return row.settledMicros;
        if (AttemptEntity.RESERVED.equals(row.state) || AttemptEntity.UNCERTAIN.equals(row.state)) return row.reservedMicros;
        return 0;
    }

    @Override public int uncertainForMonth(String monthKey) {
        requireTransaction();
        int count = 0;
        for (AttemptEntity row : rows.values()) {
            if (row.monthKey.equals(monthKey) && AttemptEntity.UNCERTAIN.equals(row.state)) count++;
        }
        return count;
    }

    @Override public BudgetSettingsEntity settings() {
        requireTransaction();
        if (settings == null) return null;
        BudgetSettingsEntity copy = new BudgetSettingsEntity();
        copy.currency = settings.currency;
        copy.dailyMicros = settings.dailyMicros;
        copy.monthlyMicros = settings.monthlyMicros;
        copy.zoneId = settings.zoneId;
        copy.updatedAt = settings.updatedAt;
        return copy;
    }

    @Override public void saveSettings(BudgetSettingsEntity value) {
        requireTransaction();
        settings = value;
    }

    @Override public int markInterrupted(String currentBootId, long now) {
        requireTransaction();
        int count = 0;
        for (AttemptEntity row : rows.values()) {
            if (AttemptEntity.RESERVED.equals(row.state) && !row.bootId.equals(currentBootId)) {
                row.state = AttemptEntity.UNCERTAIN;
                row.interrupted = true;
                row.updatedAt = now;
                count++;
            }
        }
        return count;
    }

    @Override public int forgetChannel(String channelId, long now) {
        requireTransaction();
        int count = 0;
        for (AttemptEntity row : rows.values()) {
            if (channelId.equals(row.channelId)) {
                row.channelId = null;
                row.sessionId = null;
                row.stepId = null;
                row.connectionRevision = null;
                row.updatedAt = now;
                count++;
            }
        }
        return count;
    }

    @Override public List<AttemptEntity> forMonth(String monthKey) {
        requireTransaction();
        List<AttemptEntity> result = new ArrayList<>();
        for (AttemptEntity row : rows.values()) if (row.monthKey.equals(monthKey)) result.add(copy(row));
        return result;
    }

    AttemptEntity raw(String requestId) {
        return inTransaction(() -> find(requestId));
    }

    private static AttemptEntity copy(AttemptEntity source) {
        AttemptEntity row = new AttemptEntity();
        row.requestId = source.requestId;
        row.bootId = source.bootId;
        row.sessionId = source.sessionId;
        row.channelId = source.channelId;
        row.connectionRevision = source.connectionRevision;
        row.stepId = source.stepId;
        row.taskType = source.taskType;
        row.provider = source.provider;
        row.model = source.model;
        row.zoneId = source.zoneId;
        row.dayKey = source.dayKey;
        row.monthKey = source.monthKey;
        row.currency = source.currency;
        row.priceRevision = source.priceRevision;
        row.reservedMicros = source.reservedMicros;
        row.state = source.state;
        row.settledMicros = source.settledMicros;
        row.usageState = source.usageState;
        row.inputTokens = source.inputTokens;
        row.outputTokens = source.outputTokens;
        row.totalTokens = source.totalTokens;
        row.cachedInputTokens = source.cachedInputTokens;
        row.reasoningTokens = source.reasoningTokens;
        row.interrupted = source.interrupted;
        row.createdAt = source.createdAt;
        row.updatedAt = source.updatedAt;
        return row;
    }
}
