package com.cc104.pos.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Product catalogue details and the current inventory balance. */
public record Product(String sku, String name, String category, String unit,
                      BigDecimal costPrice, BigDecimal sellingPrice,
                      int reorderLevel, int qtyOnHand, boolean active) {
    public Product {
        costPrice = money(costPrice);
        sellingPrice = money(sellingPrice);
    }

    public Product withQuantity(int quantity) {
        return new Product(sku, name, category, unit, costPrice, sellingPrice, reorderLevel, quantity, active);
    }

    public Product withActive(boolean value) {
        return new Product(sku, name, category, unit, costPrice, sellingPrice, reorderLevel, qtyOnHand, value);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
