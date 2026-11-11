package com.kofi.pos.data.csv; // CSV layer.

import com.kofi.pos.model.Product; // Record stored in products.csv.

import java.math.BigDecimal; // Exact money parsing.
import java.util.List;       // Column list.

/** Layout of products.csv. */
public final class ProductCsv {
    private ProductCsv() { } // Holder class: only exposes TABLE.

    /** Column order MUST match the header line of src/main/resources/seed/products.csv. */
    public static final CsvTable<Product> TABLE = new CsvTable<>("products.csv",
            List.of("sku", "name", "category", "variant", "size_oz", "unit", "cost_price", "selling_price",
                    "reorder_level", "qty_on_hand", "active", "image"),
            row -> new Product(row.get(0), row.get(1), row.get(2), row.get(3), Integer.parseInt(row.get(4)), row.get(5),
                    new BigDecimal(row.get(6)), new BigDecimal(row.get(7)), Integer.parseInt(row.get(8)),
                    Integer.parseInt(row.get(9)), Boolean.parseBoolean(row.get(10)), row.get(11)),
            p -> List.of(p.sku(), p.name(), p.category(), p.variant(), Integer.toString(p.sizeOz()), p.unit(),
                    p.costPrice().toPlainString(), p.sellingPrice().toPlainString(), Integer.toString(p.reorderLevel()),
                    Integer.toString(p.qtyOnHand()), Boolean.toString(p.active()), p.image()));
}
