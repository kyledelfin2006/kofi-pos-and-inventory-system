package com.kofi.pos.model; // Domain-model package.

import java.time.LocalDateTime; // Timestamp type.

/** An auditable change to a product's stock balance. */
public record StockMovement(
        long id,                    // Increasing identifier, unique per movement.
        LocalDateTime occurredAt,   // When the stock changed.
        String sku,                 // Product affected.
        String productName,         // Product name at that time.
        String type,                // "Stock In", "Stock Out" or "Adjust".
        int quantity,               // Units moved (always positive).
        String reason,              // Free-text note.
        String reference) { }       // Optional document reference, e.g. a receipt number.
