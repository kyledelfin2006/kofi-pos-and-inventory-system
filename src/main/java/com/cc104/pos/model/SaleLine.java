package com.cc104.pos.model;

import java.math.BigDecimal;

/** A historical, price-locked line on a receipt. */
public record SaleLine(String receiptNo, String sku, String productName,
                       BigDecimal unitPrice, int quantity, BigDecimal lineTotal) { }
