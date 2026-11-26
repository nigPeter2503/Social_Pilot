package com.socialpilot.app.domain.contract.ai;

import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Day and month keys of an IANA zone, pinned when an attempt is reserved so that settlement
 * after midnight or after a zone change still lands in the original period. Uses Calendar
 * because java.time is not available on API 23 without library desugaring.
 */
public final class BudgetPeriod {
    public final String zoneId;
    public final String dayKey;
    public final String monthKey;

    private BudgetPeriod(String zoneId, String dayKey, String monthKey) {
        this.zoneId = zoneId;
        this.dayKey = dayKey;
        this.monthKey = monthKey;
    }

    public static BudgetPeriod of(long epochMillis, String zoneId) {
        Objects.requireNonNull(zoneId);
        TimeZone zone = TimeZone.getTimeZone(zoneId);
        // TimeZone silently falls back to GMT for unknown IDs; refuse instead of mis-bucketing.
        if (!zone.getID().equals(zoneId)) throw new IllegalArgumentException("Unknown time zone");
        Calendar calendar = Calendar.getInstance(zone, Locale.ROOT);
        calendar.setTimeInMillis(epochMillis);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return new BudgetPeriod(zoneId, String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day),
                String.format(Locale.ROOT, "%04d-%02d", year, month));
    }

    /** Rebuilds a period that was stored with a record; keys are trusted as pinned. */
    public static BudgetPeriod pinned(String zoneId, String dayKey, String monthKey) {
        if (zoneId == null || dayKey == null || monthKey == null || !dayKey.startsWith(monthKey)) {
            throw new IllegalArgumentException("Invalid pinned period");
        }
        return new BudgetPeriod(zoneId, dayKey, monthKey);
    }
}
