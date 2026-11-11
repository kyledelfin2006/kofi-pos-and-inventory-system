package com.kofi.pos.model; // Domain-model package.

import com.kofi.pos.util.Money; // Shared rounding helper so every price has exactly two decimals.

import java.math.BigDecimal; // Exact decimal type for prices.

/** A sellable item (one size/temperature of a drink is its own product) and its current stock. */
public record Product(
        String sku,               // Unique stock-keeping code, e.g. COF-SPL-I16.
        String name,              // Base name, e.g. "Spanish Latte".
        String category,          // "Coffee" or "Food".
        String variant,           // "Hot", "Iced", or blank for items without a variant.
        int sizeOz,               // Cup size in ounces; 0 when the item has no size (food).
        String unit,              // Unit of measure: cup, order, pc ...
        BigDecimal costPrice,     // What the cafe pays per unit.
        BigDecimal sellingPrice,  // What the customer pays per unit.
        int reorderLevel,         // Restock alert triggers when stock <= this number.
        int qtyOnHand,            // Units currently in stock.
        boolean active,           // false = hidden/"deleted" but kept for history.
        String image) {           // Image file name under assets/products, or blank for the placeholder.

    /** Compact constructor: runs before the fields are assigned, so we can normalise values. */
    public Product {
        variant = variant == null ? "" : variant; // Never null, so callers can skip null checks.
        image = image == null ? "" : image;
        costPrice = Money.of(costPrice);          // Force 2 decimal places (null becomes 0.00).
        sellingPrice = Money.of(sellingPrice);    // Same for the selling price.
    }

    /** Name shown to people, e.g. "Spanish Latte · Iced 16 oz" (just the name for plain food items). */
    public String displayName() {
        String detail = (variant + (sizeOz > 0 ? " " + sizeOz + " oz" : "")).trim(); // "Iced 16 oz", "Hot", "" ...
        return detail.isEmpty() ? name : name + " · " + detail;
    }

    /** Records are immutable, so "changing" stock means building a new Product with the new quantity. */
    public Product withQuantity(int quantity) {
        return new Product(sku, name, category, variant, sizeOz, unit, costPrice, sellingPrice, reorderLevel, quantity, active, image);
    }

    /** Returns a copy with the active flag changed (used for soft-deleting products that have history). */
    public Product withActive(boolean value) {
        return new Product(sku, name, category, variant, sizeOz, unit, costPrice, sellingPrice, reorderLevel, qtyOnHand, value, image);
    }
}
