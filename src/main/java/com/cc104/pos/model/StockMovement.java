package com.cc104.pos.model;

import java.time.LocalDateTime;

/** An auditable change to a product's stock balance. */
public record StockMovement(long id, LocalDateTime occurredAt, String sku, String productName,
                            String type, int quantity, String reason, String reference) { }
