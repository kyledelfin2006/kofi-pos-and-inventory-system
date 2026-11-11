package com.cc104.pos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A completed or voided receipt. */
public record Sale(String receiptNo, LocalDateTime soldAt, String cashier,
                   BigDecimal subtotal, BigDecimal discount, BigDecimal total,
                   BigDecimal cashTendered, BigDecimal changeAmount,
                   String status, String voidReason) {
    public Sale voided(String reason) {
        return new Sale(receiptNo, soldAt, cashier, subtotal, discount, total,
                cashTendered, changeAmount, "VOID", reason);
    }
}
