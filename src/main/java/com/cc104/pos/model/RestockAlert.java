package com.cc104.pos.model; // Domain-model package.

/** Current product values shown in the FIFO low-stock queue. */
public record RestockAlert(
        String sku,         // Which product needs restocking.
        String productName, // Its display name.
        int quantity,       // Units on hand when the alert was built.
        int reorderLevel) { } // The threshold at or below which the alert exists.
