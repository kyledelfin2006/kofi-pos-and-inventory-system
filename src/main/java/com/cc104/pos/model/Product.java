package com.cc104.pos.model; // Domain-model package.

import com.cc104.pos.util.Money; // Shared rounding helper so every price has exactly two decimals.

import java.math.BigDecimal; // Exact decimal type for prices.

/** Product catalogue details and the current inventory balance. */
public record Product(
        String sku,               // Unique stock-keeping code, e.g. SKU-1001.
        String name,              // Display name.
        String category,          // Free-text group, may be blank.
        String unit,              // Unit of measure: pc, kg, pack ...
        BigDecimal costPrice,     // What the store pays per unit.
        BigDecimal sellingPrice,  // What the customer pays per unit.
        int reorderLevel,         // Restock alert triggers when stock <= this number.
        int qtyOnHand,            // Units currently in stock.
        boolean active) {         // false = hidden/"deleted" but kept for history.

    /** Compact constructor: runs before the fields are assigned, so we can normalise values. */
    public Product {
        costPrice = Money.of(costPrice);       // Force 2 decimal places (null becomes 0.00).
        sellingPrice = Money.of(sellingPrice); // Same for the selling price.
    }

    /** Records are immutable, so "changing" stock means building a new Product with the new quantity. */
    public Product withQuantity(int quantity) {
        return new Product(sku, name, category, unit, costPrice, sellingPrice, reorderLevel, quantity, active);
    }

    /** Returns a copy with the active flag changed (used for soft-deleting products that have history). */
    public Product withActive(boolean value) {
        return new Product(sku, name, category, unit, costPrice, sellingPrice, reorderLevel, qtyOnHand, value);
    }
}
