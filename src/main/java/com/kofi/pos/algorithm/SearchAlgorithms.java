package com.kofi.pos.algorithm; // Hand-written algorithms required by the course.

import java.util.List;                   // Generic list interface.
import java.util.function.Predicate;     // "Does this element match?" test.
import java.util.function.ToIntFunction; // "How does this element compare with the key?" test.

/** Hand-written searching algorithms: one linear, one binary. */
public final class SearchAlgorithms {
    private SearchAlgorithms() { } // Utility class.

    /**
     * Linear search: look at elements one by one from the front until one matches.
     * Works on UNSORTED data. O(n).
     *
     * @return index of the first match, or -1 when nothing matches
     */
    public static <T> int linearSearch(List<T> items, Predicate<T> matches) {
        for (int i = 0; i < items.size(); i++) {      // Visit every position in order.
            if (matches.test(items.get(i))) return i; // Stop at the first element that satisfies the test.
        }
        return -1; // Walked the whole list without a match.
    }

    /**
     * Binary search: on a list ALREADY SORTED by the same ordering, repeatedly inspect the middle
     * element and discard the half that cannot contain the key. O(log n).
     *
     * @param sortedItems  list sorted ascending
     * @param compareToKey returns negative if the element sorts before the key, 0 if equal, positive if after
     * @return index of the matching element, or -1 when absent
     */
    public static <T> int binarySearch(List<T> sortedItems, ToIntFunction<T> compareToKey) {
        int low = 0;                       // Left edge of the range still under consideration.
        int high = sortedItems.size() - 1; // Right edge of that range.
        while (low <= high) {              // Keep going while at least one candidate remains.
            int mid = low + (high - low) / 2; // Middle index (written this way to avoid int overflow).
            int result = compareToKey.applyAsInt(sortedItems.get(mid)); // Compare the middle element with the key.
            if (result == 0) return mid;   // Found it.
            if (result < 0) low = mid + 1; // Middle is too small: the key can only be in the right half.
            else high = mid - 1;           // Middle is too big: the key can only be in the left half.
        }
        return -1; // The range became empty, so the key is not present.
    }
}
