package com.socialpilot.app.testing;

import com.socialpilot.app.domain.contract.ai.UsageLog;
import com.socialpilot.app.domain.contract.ai.UsageRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Labelled in-memory fake; the durable implementation is the SP-029 ledger. */
public final class MemoryUsageLog implements UsageLog {
    public final Map<String, UsageRecord> records = new LinkedHashMap<>();

    @Override public synchronized boolean record(UsageRecord record) {
        if (records.containsKey(record.requestId)) return false;
        records.put(record.requestId, record);
        return true;
    }

    @Override public synchronized List<UsageRecord> forMonth(String monthKey) {
        List<UsageRecord> result = new ArrayList<>();
        for (UsageRecord record : records.values()) if (record.period.monthKey.equals(monthKey)) result.add(record);
        return result;
    }
}
