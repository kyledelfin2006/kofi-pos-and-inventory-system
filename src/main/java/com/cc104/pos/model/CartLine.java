package com.cc104.pos.model;

/** A product and quantity currently being prepared for checkout. */
public record CartLine(Product product, int quantity) { }
