package com.kofi.pos.model; // Domain-model package.

/** A product and quantity currently being prepared for checkout. */
public record CartLine(
        Product product, // The product being bought.
        int quantity) { } // How many units of it are in the cart.
