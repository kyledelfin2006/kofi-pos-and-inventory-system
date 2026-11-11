package com.cc104.pos.model;

/** Current product values shown in the FIFO low-stock queue. */
public record RestockAlert(String sku, String productName, int quantity, int reorderLevel) { }
