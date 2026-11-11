package com.kofi.pos.model; // Domain-model package.

import java.math.BigDecimal; // Exact decimal type: never use double for money.

/** A historical, price-locked line on a receipt. */
public record SaleLine(
        String receiptNo,       // Receipt this line belongs to.
        String sku,             // Product code at the time of sale.
        String productName,     // Product name copied at sale time (so later renames don't change history).
        BigDecimal unitPrice,   // Price per unit at sale time.
        int quantity,           // Units sold.
        BigDecimal lineTotal) { } // unitPrice x quantity.
