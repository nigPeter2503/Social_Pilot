package com.socialpilot.app.domain.ai;

import java.math.BigDecimal;

/** Parses a typed money amount into micros. Pure so the UI rules are unit tested. */
public final class BudgetInput {
    /** Upper bound that keeps sums of many reservations far from long overflow. */
    public static final long MAX_MICROS = 1_000_000L * 1_000_000L;

    private BudgetInput() {
    }

    /** Accepts "5", "5.25" or "5,25" with at most 6 decimals; null when invalid, zero or too large. */
    public static Long parseMicros(String text) {
        if (text == null) return null;
        String trimmed = text.trim().replace(',', '.');
        if (trimmed.isEmpty() || !trimmed.matches("\\d{1,7}(\\.\\d{1,6})?")) return null;
        long micros = new BigDecimal(trimmed).movePointRight(6).longValueExact();
        return micros <= 0 || micros > MAX_MICROS ? null : micros;
    }

    /** Plain decimal text for micros, without trailing zeros. */
    public static String format(long micros) {
        return BigDecimal.valueOf(micros, 6).stripTrailingZeros().toPlainString();
    }
}
