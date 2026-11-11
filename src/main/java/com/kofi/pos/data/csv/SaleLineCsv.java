package com.kofi.pos.data.csv; // CSV layer.

import com.kofi.pos.model.SaleLine; // Record stored in sale_lines.csv.

import java.math.BigDecimal; // Exact money parsing.
import java.util.List;       // Column list.

/** Layout of sale_lines.csv. */
public final class SaleLineCsv {
    private SaleLineCsv() { } // Holder class.

    public static final CsvTable<SaleLine> TABLE = new CsvTable<>("sale_lines.csv",
            List.of("receipt_no", "sku", "product_name", "unit_price", "quantity", "line_total"),
            row -> new SaleLine(row.get(0), row.get(1), row.get(2), new BigDecimal(row.get(3)),
                    Integer.parseInt(row.get(4)), new BigDecimal(row.get(5))),
            l -> List.of(l.receiptNo(), l.sku(), l.productName(), l.unitPrice().toPlainString(),
                    Integer.toString(l.quantity()), l.lineTotal().toPlainString()));
}
