package com.cc104.pos.model; // Domain-model package.

import java.math.BigDecimal;   // Exact decimal type for money.
import java.time.LocalDateTime; // Timestamp type.

/** A completed or voided receipt. */
public record Sale(
        String receiptNo,          // Unique receipt id such as R-20261112-0001.
        LocalDateTime soldAt,      // When the sale was completed.
        String cashier,            // Username of the cashier who made the sale.
        BigDecimal subtotal,       // Sum of the lines before discount.
        BigDecimal discount,       // Peso discount applied to the whole receipt.
        BigDecimal total,          // subtotal - discount.
        BigDecimal cashTendered,   // Cash handed over by the customer.
        BigDecimal changeAmount,   // cashTendered - total.
        String status,             // "COMPLETED" or "VOID".
        String voidReason) {       // Why the sale was voided (blank otherwise).

    /** Returns a copy of this sale marked VOID with the given reason; the original record is unchanged. */
    public Sale voided(String reason) {
        // Copy every field as-is, but force the status to "VOID" and store the reason.
        return new Sale(receiptNo, soldAt, cashier, subtotal, discount, total,
                cashTendered, changeAmount, "VOID", reason);
    }
}
