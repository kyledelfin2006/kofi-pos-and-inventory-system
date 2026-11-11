package com.cc104.pos.algorithm; // Hand-written algorithms required by the course; no UI or file code here.

import java.util.ArrayList;  // Resizable-array list used for the working copy.
import java.util.Comparator; // Lets the caller decide what "smaller" means.
import java.util.List;       // Generic list interface.

/**
 * Two hand-written sorting algorithms (course requirement: do NOT rely on Collections.sort()).
 * Both return a NEW sorted list and leave the input untouched.
 */
public final class SortAlgorithms {
    private SortAlgorithms() { } // Utility class.

    /**
     * Insertion sort: grow a sorted prefix on the left; take the next value and slide it left
     * past every larger value until it fits. Stable. O(n^2) worst case, fast on nearly sorted data.
     */
    public static <T> List<T> insertionSort(List<T> source, Comparator<? super T> order) {
        ArrayList<T> sorted = new ArrayList<>(source);        // Work on a copy so the caller's list is safe.
        for (int i = 1; i < sorted.size(); i++) {             // Index 0 alone is already "sorted", so start at 1.
            T value = sorted.get(i);                          // The element we are inserting.
            int j = i - 1;                                    // Start comparing with the element just left of it.
            while (j >= 0 && order.compare(sorted.get(j), value) > 0) { // While the left neighbour belongs AFTER value...
                sorted.set(j + 1, sorted.get(j));             // ...shift that neighbour one slot to the right,
                j--;                                          // ...and look one position further left.
            }
            sorted.set(j + 1, value);                         // Drop the value into the gap that opened up.
        }
        return sorted;                                        // Fully sorted copy.
    }

    /**
     * Selection sort: repeatedly find the "first" remaining element and swap it into place.
     * Always O(n^2) comparisons but only n-1 swaps.
     */
    public static <T> List<T> selectionSort(List<T> source, Comparator<? super T> order) {
        ArrayList<T> sorted = new ArrayList<>(source);        // Work on a copy.
        for (int i = 0; i < sorted.size() - 1; i++) {         // Position i is the next slot to fill.
            int best = i;                                     // Assume the element already at i is the best so far.
            for (int j = i + 1; j < sorted.size(); j++) {     // Scan everything to the right of i.
                if (order.compare(sorted.get(j), sorted.get(best)) < 0) best = j; // Remember a better candidate.
            }
            T temp = sorted.get(i);                           // Three-step swap: save slot i,
            sorted.set(i, sorted.get(best));                  // put the best element into slot i,
            sorted.set(best, temp);                           // and move the old occupant to where best was.
        }
        return sorted;                                        // Fully sorted copy.
    }
}
