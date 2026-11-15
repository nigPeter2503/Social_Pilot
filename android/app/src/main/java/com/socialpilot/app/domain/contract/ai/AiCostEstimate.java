package com.socialpilot.app.domain.contract.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/** An estimate in millionths of a currency unit, never an invoice amount. */
public final class AiCostEstimate {
    public final long micros;
    public final Currency currency;
    public final String priceRevision;

    private AiCostEstimate(long micros, Currency currency, String priceRevision) {
        this.micros = micros;
        this.currency = Objects.requireNonNull(currency);
        if (priceRevision == null || priceRevision.trim().isEmpty()) {
            throw new IllegalArgumentException("Price revision is required");
        }
        this.priceRevision = priceRevision;
    }

    /**
     * Rates are currency units per million tokens. The caller maps provider usage to mutually
     * exclusive billable counts and supplies the appropriate rates for the model and tier.
     */
    public static AiCostEstimate text(long uncachedInput, long cachedInput, long output,
                                      BigDecimal uncachedRate, BigDecimal cachedRate,
                                      BigDecimal outputRate, Currency currency, String priceRevision) {
        BigDecimal micros = price(uncachedInput, uncachedRate)
                .add(price(cachedInput, cachedRate)).add(price(output, outputRate));
        return new AiCostEstimate(micros.setScale(0, RoundingMode.CEILING).longValueExact(),
                currency, priceRevision);
    }

    private static BigDecimal price(long tokens, BigDecimal rate) {
        if (tokens < 0 || Objects.requireNonNull(rate).signum() < 0) {
            throw new IllegalArgumentException("Negative count or rate");
        }
        return rate.multiply(BigDecimal.valueOf(tokens));
    }
}
