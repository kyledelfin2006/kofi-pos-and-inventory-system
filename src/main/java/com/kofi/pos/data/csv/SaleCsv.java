package com.kofi.pos.data.csv; // CSV layer.

import com.kofi.pos.model.Sale; // Record stored in sales.csv.

import java.math.BigDecimal;    // Exact money parsing.
import java.time.LocalDateTime; // ISO timestamp parsing.
import java.util.List;          // Column list.

/** Layout of sales.csv. */
public final class SaleCsv {
    private SaleCsv() { } // Holder class.

    public static final CsvTable<Sale> TABLE = new CsvTable<>("sales.csv",
            List.of("receipt_no", "sold_at", "cashier", "subtotal", "discount", "total",
                    "cash_tendered", "change_amount", "status", "void_reason"),
            row -> new Sale(row.get(0), LocalDateTime.parse(row.get(1)), row.get(2),
                    new BigDecimal(row.get(3)), new BigDecimal(row.get(4)), new BigDecimal(row.get(5)),
                    new BigDecimal(row.get(6)), new BigDecimal(row.get(7)), row.get(8), row.get(9)),
            s -> List.of(s.receiptNo(), s.soldAt().toString(), s.cashier(), s.subtotal().toPlainString(),
                    s.discount().toPlainString(), s.total().toPlainString(), s.cashTendered().toPlainString(),
                    s.changeAmount().toPlainString(), s.status(), s.voidReason()));
}
