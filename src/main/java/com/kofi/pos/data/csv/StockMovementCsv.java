package com.kofi.pos.data.csv; // CSV layer.

import com.kofi.pos.model.StockMovement; // Record stored in stock_movements.csv.

import java.time.LocalDateTime; // ISO timestamp parsing.
import java.util.List;          // Column list.

/** Layout of stock_movements.csv. */
public final class StockMovementCsv {
    private StockMovementCsv() { } // Holder class.

    public static final CsvTable<StockMovement> TABLE = new CsvTable<>("stock_movements.csv",
            List.of("id", "occurred_at", "sku", "product_name", "type", "quantity", "reason", "reference"),
            row -> new StockMovement(Long.parseLong(row.get(0)), LocalDateTime.parse(row.get(1)), row.get(2),
                    row.get(3), row.get(4), Integer.parseInt(row.get(5)), row.get(6), row.get(7)),
            m -> List.of(Long.toString(m.id()), m.occurredAt().toString(), m.sku(), m.productName(), m.type(),
                    Integer.toString(m.quantity()), m.reason(), m.reference()));
}
