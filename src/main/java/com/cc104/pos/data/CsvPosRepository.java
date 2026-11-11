package com.cc104.pos.data; // Persistence layer.

import com.cc104.pos.model.Product;       // Catalogue record.
import com.cc104.pos.model.Sale;          // Receipt header record.
import com.cc104.pos.model.SaleLine;      // Receipt line record.
import com.cc104.pos.model.Settings;      // Store settings record.
import com.cc104.pos.model.StockMovement; // Stock audit record.
import com.cc104.pos.model.User;          // Login record.

import java.io.IOException;          // Storage failure.
import java.math.BigDecimal;         // Parses money text exactly.
import java.time.LocalDateTime;      // Parses ISO timestamps.
import java.util.ArrayList;          // Resizable list.
import java.util.List;               // Generic list interface.
import java.util.function.Function;  // Row-to-object / object-to-row converters.

/** Reads and writes the application's domain records using the local CSV files. */
public final class CsvPosRepository implements PosRepository {
    // Column order of each file. The first CSV line of every file must match these names.
    private static final List<String> PRODUCT_COLUMNS = List.of("sku", "name", "category", "unit", "cost_price", "selling_price", "reorder_level", "qty_on_hand", "active");
    private static final List<String> SALE_COLUMNS = List.of("receipt_no", "sold_at", "cashier", "subtotal", "discount", "total", "cash_tendered", "change_amount", "status", "void_reason");
    private static final List<String> SALE_LINE_COLUMNS = List.of("receipt_no", "sku", "product_name", "unit_price", "quantity", "line_total");
    private static final List<String> MOVEMENT_COLUMNS = List.of("id", "occurred_at", "sku", "product_name", "type", "quantity", "reason", "reference");
    private static final List<String> SETTINGS_COLUMNS = List.of("store_name", "store_address", "receipt_footer");

    private final CsvStore csv; // Low-level file helper (parsing, writing, defaults).

    public CsvPosRepository(CsvStore csv) {
        this.csv = csv; // Injected so the repository can be tested with a different store.
    }

    /** Loads everything from the CSV files, creating them from the defaults on first run. */
    @Override
    public StoreData load() throws IOException {
        csv.initialize(); // Make sure the data folder and the six files exist.
        return readSnapshot();
    }

    /** RESET: restore the default CSV files, then read them back as the new snapshot. */
    @Override
    public StoreData resetToDefaults() throws IOException {
        csv.restoreDefaults(); // Overwrite the six live files with the packaged defaults.
        return readSnapshot();
    }

    /** Converts every CSV row into a typed record. */
    private StoreData readSnapshot() throws IOException {
        // Each lambda receives one row (list of strings) and builds one record from its columns by position.
        List<Product> products = read("products.csv", row -> new Product(row.get(0), row.get(1), row.get(2), row.get(3),
                new BigDecimal(row.get(4)), new BigDecimal(row.get(5)), Integer.parseInt(row.get(6)),
                Integer.parseInt(row.get(7)), Boolean.parseBoolean(row.get(8))));
        List<User> users = read("users.csv", row -> new User(row.get(0), row.get(1), row.get(2)));
        List<Sale> sales = read("sales.csv", row -> new Sale(row.get(0), LocalDateTime.parse(row.get(1)), row.get(2),
                new BigDecimal(row.get(3)), new BigDecimal(row.get(4)), new BigDecimal(row.get(5)),
                new BigDecimal(row.get(6)), new BigDecimal(row.get(7)), row.get(8), row.get(9)));
        List<SaleLine> lines = read("sale_lines.csv", row -> new SaleLine(row.get(0), row.get(1), row.get(2),
                new BigDecimal(row.get(3)), Integer.parseInt(row.get(4)), new BigDecimal(row.get(5))));
        List<StockMovement> movements = read("stock_movements.csv", row -> new StockMovement(Long.parseLong(row.get(0)),
                LocalDateTime.parse(row.get(1)), row.get(2), row.get(3), row.get(4), Integer.parseInt(row.get(5)), row.get(6), row.get(7)));

        List<List<String>> settingsRows = csv.read("settings.csv"); // Settings has exactly one data row.
        if (settingsRows.size() <= 1) throw new IOException("settings.csv has no store settings record."); // Header only = broken.
        List<String> settings = settingsRows.get(1); // Row 0 is the header, row 1 is the record.
        return new StoreData(products, sales, lines, movements, users,
                new Settings(settings.get(0), settings.get(1), settings.get(2)));
    }

    /** Writes every collection back to its CSV file. */
    @Override
    public void save(StoreData data) throws IOException {
        // Each lambda turns one record into a row of strings in the same column order as the header.
        csv.write("products.csv", PRODUCT_COLUMNS, rows(data.products(), product -> List.of(
                product.sku(), product.name(), product.category(), product.unit(), product.costPrice().toPlainString(),
                product.sellingPrice().toPlainString(), Integer.toString(product.reorderLevel()),
                Integer.toString(product.qtyOnHand()), Boolean.toString(product.active()))));
        csv.write("sales.csv", SALE_COLUMNS, rows(data.sales(), sale -> List.of(sale.receiptNo(), sale.soldAt().toString(),
                sale.cashier(), sale.subtotal().toPlainString(), sale.discount().toPlainString(), sale.total().toPlainString(),
                sale.cashTendered().toPlainString(), sale.changeAmount().toPlainString(), sale.status(), sale.voidReason())));
        csv.write("sale_lines.csv", SALE_LINE_COLUMNS, rows(data.saleLines(), line -> List.of(line.receiptNo(), line.sku(),
                line.productName(), line.unitPrice().toPlainString(), Integer.toString(line.quantity()), line.lineTotal().toPlainString())));
        csv.write("stock_movements.csv", MOVEMENT_COLUMNS, rows(data.movements(), movement -> List.of(
                Long.toString(movement.id()), movement.occurredAt().toString(), movement.sku(), movement.productName(),
                movement.type(), Integer.toString(movement.quantity()), movement.reason(), movement.reference())));
        Settings settings = data.settings(); // Single settings record.
        csv.write("settings.csv", SETTINGS_COLUMNS,
                List.of(List.of(settings.storeName(), settings.storeAddress(), settings.receiptFooter())));
    }

    /** Reads a file and converts every row after the header using {@code mapper}. */
    private <T> List<T> read(String file, Function<List<String>, T> mapper) throws IOException {
        List<List<String>> rows = csv.read(file);   // Raw rows, header included.
        ArrayList<T> values = new ArrayList<>();    // Typed results.
        for (int index = 1; index < rows.size(); index++) values.add(mapper.apply(rows.get(index))); // Start at 1 to skip the header.
        return values;
    }

    /** Converts every record in {@code source} to a CSV row using {@code mapper}. */
    private static <T> List<List<String>> rows(List<T> source, Function<T, List<String>> mapper) {
        ArrayList<List<String>> records = new ArrayList<>(source.size()); // Pre-sized result list.
        for (T value : source) records.add(mapper.apply(value));           // Convert each record.
        return records;
    }
}
