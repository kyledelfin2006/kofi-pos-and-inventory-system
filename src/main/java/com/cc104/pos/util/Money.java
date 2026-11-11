package com.cc104.pos.util; // Small helpers with no dependency on any other layer.

import java.math.BigDecimal;   // Exact decimal arithmetic.
import java.math.RoundingMode; // Rounding rule selector.

/** One place that defines how money is rounded across the whole application. */
public final class Money {
    /** The value 0.00, shared so we do not create it repeatedly. */
    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private Money() { } // Utility class: the private constructor stops anyone from instantiating it.

    /** Rounds to two decimal places (half-up, like a cashier would); null is treated as zero. */
    public static BigDecimal of(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
