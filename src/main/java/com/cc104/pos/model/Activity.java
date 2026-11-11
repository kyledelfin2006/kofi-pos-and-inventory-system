package com.cc104.pos.model; // Domain-model package: plain immutable data holders, no logic about files or screens.

import java.time.LocalDateTime; // Date-and-time type used to stamp the activity.

/** A short dashboard entry describing a recent store action. */
// A Java "record" is an immutable class: the compiler writes the constructor, getters, equals and hashCode.
public record Activity(
        LocalDateTime occurredAt, // When the action happened.
        String action,            // Short label such as "Sale completed" or "Stock In".
        String detail) { }        // Extra text such as the receipt number and total.
