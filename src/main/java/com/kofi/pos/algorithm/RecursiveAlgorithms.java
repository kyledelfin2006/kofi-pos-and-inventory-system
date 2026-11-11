package com.kofi.pos.algorithm; // Hand-written algorithms required by the course.

import java.math.BigDecimal;        // Exact decimal sums.
import java.util.List;              // Generic list interface.
import java.util.function.Function; // Converts an element into the amount to add.

/** Recursive operations (course requirement: at least one recursive operation). */
public final class RecursiveAlgorithms {
    private RecursiveAlgorithms() { } // Utility class.

    /** Sums {@code amountOf(item)} for every item in the list using recursion instead of a loop. */
    public static <T> BigDecimal sum(List<T> items, Function<T, BigDecimal> amountOf) {
        return sumFrom(items, 0, amountOf); // Start the recursion at the first element.
    }

    /**
     * Recursive helper. Base case: no elements left, so the sum is zero.
     * Recursive case: this element's amount + the sum of everything after it.
     */
    private static <T> BigDecimal sumFrom(List<T> items, int index, Function<T, BigDecimal> amountOf) {
        if (index >= items.size()) return BigDecimal.ZERO; // BASE CASE: past the end, nothing left to add.
        // RECURSIVE CASE: current amount plus the answer for the smaller problem (index + 1 onward).
        return amountOf.apply(items.get(index)).add(sumFrom(items, index + 1, amountOf));
    }
}
