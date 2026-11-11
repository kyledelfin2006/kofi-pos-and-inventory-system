package com.cc104.pos.data;

import com.cc104.pos.model.Product;
import com.cc104.pos.model.Sale;
import com.cc104.pos.model.SaleLine;
import com.cc104.pos.model.Settings;
import com.cc104.pos.model.StockMovement;
import com.cc104.pos.model.User;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Reads and writes the application's domain records using the local CSV files. */
public final class CsvPosRepository implements PosRepository {
    private static final List<String> PRODUCT_COLUMNS = List.of("sku", "name", "category", "unit", "cost_price", "selling_price", "reorder_level", "qty_on_hand", "active");
    private static final List<String> SALE_COLUMNS = List.of("receipt_no", "sold_at", "cashier", "subtotal", "discount", "total", "cash_tendered", "change_amount", "status", "void_reason");
    private static final List<String> SALE_LINE_COLUMNS = List.of("receipt_no", "sku", "product_name", "unit_price", "quantity", "line_total");
    private static final List<String> MOVEMENT_COLUMNS = List.of("id", "occurred_at", "sku", "product_name", "type", "quantity", "reason", "reference");
    private static final List<String> SETTINGS_COLUMNS = List.of("store_name", "store_address", "receipt_footer");

    private final CsvStore csv;

    public CsvPosRepository(CsvStore csv) {
        this.csv = csv;
    }

    @Override
    public StoreData load() throws IOException {
        csv.initialize();
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

        List<List<String>> settingsRows = csv.read("settings.csv");
        if (settingsRows.size() <= 1) throw new IOException("settings.csv has no store settings record.");
        List<String> settings = settingsRows.get(1);
        return new StoreData(products, sales, lines, movements, users,
                new Settings(settings.get(0), settings.get(1), settings.get(2)));
    }

    @Override
    public void save(StoreData data) throws IOException {
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
        Settings settings = data.settings();
        csv.write("settings.csv", SETTINGS_COLUMNS,
                List.of(List.of(settings.storeName(), settings.storeAddress(), settings.receiptFooter())));
    }

    private <T> List<T> read(String file, Function<List<String>, T> mapper) throws IOException {
        List<List<String>> rows = csv.read(file);
        ArrayList<T> values = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) values.add(mapper.apply(rows.get(index)));
        return values;
    }

    private static <T> List<List<String>> rows(List<T> source, Function<T, List<String>> mapper) {
        ArrayList<List<String>> records = new ArrayList<>(source.size());
        for (T value : source) records.add(mapper.apply(value));
        return records;
    }
}
