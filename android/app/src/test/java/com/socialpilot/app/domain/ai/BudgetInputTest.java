package com.socialpilot.app.domain.ai;

import static org.junit.Assert.*;

import org.junit.Test;

public final class BudgetInputTest {
    @Test public void parsesDecimalAmountsIntoMicros() {
        assertEquals(Long.valueOf(5_000_000), BudgetInput.parseMicros("5"));
        assertEquals(Long.valueOf(5_250_000), BudgetInput.parseMicros(" 5.25 "));
        assertEquals(Long.valueOf(5_250_000), BudgetInput.parseMicros("5,25"));
        assertEquals(Long.valueOf(1), BudgetInput.parseMicros("0.000001"));
        assertEquals(Long.valueOf(BudgetInput.MAX_MICROS), BudgetInput.parseMicros("1000000"));
    }

    @Test public void rejectsZeroNegativeTooPreciseOrTooLarge() {
        for (String bad : new String[] {null, "", " ", "0", "0.0", "-1", "1.0000001", "1e3", "abc", "1.2.3",
                "1000000.000001", "99999999"}) {
            assertNull(bad, BudgetInput.parseMicros(bad));
        }
    }

    @Test public void formatsWithoutTrailingZeros() {
        assertEquals("5.25", BudgetInput.format(5_250_000));
        assertEquals("0", BudgetInput.format(0));
        assertEquals("0.000001", BudgetInput.format(1));
    }
}
