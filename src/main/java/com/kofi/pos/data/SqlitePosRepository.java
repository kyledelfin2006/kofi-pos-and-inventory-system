package com.kofi.pos.data;

import com.kofi.pos.model.Product;
import com.kofi.pos.model.Sale;
import com.kofi.pos.model.SaleLine;
import com.kofi.pos.model.Settings;
import com.kofi.pos.model.StockMovement;
import com.kofi.pos.model.User;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** SQLite-backed repository for the six kinds of application records. */
public final class SqlitePosRepository implements PosRepository {
    private static final Path DATA_DIRECTORY = Path.of("data");
    private static final String DATABASE_FILE = "kofi-pos.db";
    private static final String[] LEGACY_FILES = {
            "products.csv", "users.csv", "sales.csv", "sale_lines.csv", "stock_movements.csv", "settings.csv"
    };

    private static final List<String> PRODUCT_COLUMNS = List.of("sku", "name", "category", "variant", "size_oz", "unit",
            "cost_price", "selling_price", "reorder_level", "qty_on_hand", "active", "image");
    private static final List<String> USER_COLUMNS = List.of("username", "salt", "password_hash");
    private static final List<String> SALE_COLUMNS = List.of("receipt_no", "sold_at", "cashier", "subtotal", "discount", "total",
            "cash_tendered", "change_amount", "status", "void_reason");
    private static final List<String> SALE_LINE_COLUMNS = List.of("receipt_no", "sku", "product_name", "unit_price", "quantity", "line_total");
    private static final List<String> MOVEMENT_COLUMNS = List.of("id", "occurred_at", "sku", "product_name", "type", "quantity", "reason", "reference");
    private static final List<String> SETTINGS_COLUMNS = List.of("store_name", "store_address", "receipt_footer");

    private final Path database = DATA_DIRECTORY.resolve(DATABASE_FILE);
    private boolean initialized;

    @Override
    public StoreData load() throws IOException {
        initialize();
        try (Connection connection = open()) {
            return readSnapshot(connection);
        } catch (SQLException e) {
            throw storageError("Could not read the local database", e);
        }
    }

    @Override
    public void save(StoreData data) throws IOException {
        initialize();
        transaction(connection -> {
            clearLiveData(connection);
            insertProducts(connection, data.products());
            insertSales(connection, data.sales());
            insertSaleLines(connection, data.saleLines());
            insertMovements(connection, data.movements());
            insertUsers(connection, data.users());
            insertSettings(connection, data.settings());
            return null;
        });
    }

    @Override
    public StoreData resetToDefaults() throws IOException {
        initialize();
        return transaction(connection -> {
            clearLiveData(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM default_products");
            }
            applySeed(connection);
            return readSnapshot(connection);
        });
    }

    @Override
    public List<Product> defaultProducts() throws IOException {
        initialize();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(productSelect("default_products"));
             ResultSet rows = statement.executeQuery()) {
            return readProducts(rows);
        } catch (SQLException e) {
            throw storageError("Could not read the default products", e);
        }
    }

    private void initialize() throws IOException {
        if (initialized) return;
        Path parent = database.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        boolean migratedLegacyFiles = false;
        try (Connection connection = open()) {
            createSchema(connection);
            if (!isInitialized(connection)) {
                boolean hasLegacyFiles = hasLegacyFiles();
                connection.setAutoCommit(false);
                try {
                    clearLiveData(connection);
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("DELETE FROM default_products");
                    }
                    applySeed(connection);
                    if (hasLegacyFiles) importLegacyFiles(connection);
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT OR REPLACE INTO app_meta (key, value) VALUES ('initialized', 'true')")) {
                        statement.executeUpdate();
                    }
                    connection.commit();
                    migratedLegacyFiles = hasLegacyFiles;
                } catch (SQLException | IOException e) {
                    rollback(connection, e);
                    throw e;
                } catch (RuntimeException e) {
                    rollback(connection, e);
                    throw new IOException("Legacy CSV data could not be migrated", e);
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE default_products SET qty_on_hand = 50");
            }
        } catch (SQLException e) {
            throw storageError("Could not initialize the local database", e);
        }
        initialized = true;
        if (migratedLegacyFiles) removeLegacyFiles();
    }

    private Connection open() throws SQLException {
        String location = database.toAbsolutePath().toString().replace('\\', '/');
        return DriverManager.getConnection("jdbc:sqlite:" + location);
    }

    private static void createSchema(Connection connection) throws SQLException {
        List<String> schema = List.of(
                "CREATE TABLE IF NOT EXISTS app_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS default_products (position INTEGER NOT NULL, sku TEXT NOT NULL COLLATE NOCASE PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, variant TEXT NOT NULL, size_oz INTEGER NOT NULL, unit TEXT NOT NULL, cost_price TEXT NOT NULL, selling_price TEXT NOT NULL, reorder_level INTEGER NOT NULL, qty_on_hand INTEGER NOT NULL, active INTEGER NOT NULL, image TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS products (position INTEGER NOT NULL, sku TEXT NOT NULL COLLATE NOCASE PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL, variant TEXT NOT NULL, size_oz INTEGER NOT NULL, unit TEXT NOT NULL, cost_price TEXT NOT NULL, selling_price TEXT NOT NULL, reorder_level INTEGER NOT NULL, qty_on_hand INTEGER NOT NULL, active INTEGER NOT NULL, image TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS users (position INTEGER NOT NULL, username TEXT NOT NULL COLLATE NOCASE PRIMARY KEY, salt TEXT NOT NULL, password_hash TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS sales (position INTEGER NOT NULL, receipt_no TEXT PRIMARY KEY, sold_at TEXT NOT NULL, cashier TEXT NOT NULL, subtotal TEXT NOT NULL, discount TEXT NOT NULL, total TEXT NOT NULL, cash_tendered TEXT NOT NULL, change_amount TEXT NOT NULL, status TEXT NOT NULL, void_reason TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS sale_lines (position INTEGER NOT NULL, receipt_no TEXT NOT NULL, sku TEXT NOT NULL, product_name TEXT NOT NULL, unit_price TEXT NOT NULL, quantity INTEGER NOT NULL, line_total TEXT NOT NULL, PRIMARY KEY (receipt_no, sku))",
                "CREATE TABLE IF NOT EXISTS stock_movements (position INTEGER NOT NULL, id INTEGER PRIMARY KEY, occurred_at TEXT NOT NULL, sku TEXT NOT NULL, product_name TEXT NOT NULL, type TEXT NOT NULL, quantity INTEGER NOT NULL, reason TEXT NOT NULL, reference TEXT NOT NULL)",
                "CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY CHECK (id = 1), store_name TEXT NOT NULL, store_address TEXT NOT NULL, receipt_footer TEXT NOT NULL)"
        );
        try (Statement statement = connection.createStatement()) {
            for (String sql : schema) statement.executeUpdate(sql);
        }
    }

    private static boolean isInitialized(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM app_meta WHERE key = 'initialized'")) {
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void applySeed(Connection connection) throws IOException, SQLException {
        try (InputStream input = SqlitePosRepository.class.getResourceAsStream("/seed/seed.sql")) {
            if (input == null) throw new IOException("Missing starter database script: /seed/seed.sql");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.isBlank()) statement.executeUpdate(command);
                }
            }
        }
    }

    private static StoreData readSnapshot(Connection connection) throws SQLException, IOException {
        List<Product> products = readProducts(connection, "products");
        List<Sale> sales = readSales(connection);
        List<SaleLine> saleLines = readSaleLines(connection);
        List<StockMovement> movements = readMovements(connection);
        List<User> users = readUsers(connection);
        Settings settings = readSettings(connection);
        if (settings == null) throw new IOException("The database has no store settings record.");
        return new StoreData(products, sales, saleLines, movements, users, settings);
    }

    private static String productSelect(String table) {
        return "SELECT sku, name, category, variant, size_oz, unit, cost_price, selling_price, reorder_level, qty_on_hand, active, image FROM "
                + table + " ORDER BY position";
    }

    private static List<Product> readProducts(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(productSelect(table));
             ResultSet rows = statement.executeQuery()) {
            return readProducts(rows);
        }
    }

    private static List<Product> readProducts(ResultSet rows) throws SQLException {
        ArrayList<Product> values = new ArrayList<>();
        while (rows.next()) values.add(new Product(rows.getString("sku"), rows.getString("name"), rows.getString("category"),
                rows.getString("variant"), rows.getInt("size_oz"), rows.getString("unit"), decimal(rows, "cost_price"),
                decimal(rows, "selling_price"), rows.getInt("reorder_level"), rows.getInt("qty_on_hand"),
                rows.getInt("active") != 0, rows.getString("image")));
        return values;
    }

    private static List<Sale> readSales(Connection connection) throws SQLException {
        ArrayList<Sale> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM sales ORDER BY position");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) values.add(new Sale(rows.getString("receipt_no"), LocalDateTime.parse(rows.getString("sold_at")),
                    rows.getString("cashier"), decimal(rows, "subtotal"), decimal(rows, "discount"), decimal(rows, "total"),
                    decimal(rows, "cash_tendered"), decimal(rows, "change_amount"), rows.getString("status"), rows.getString("void_reason")));
        }
        return values;
    }

    private static List<SaleLine> readSaleLines(Connection connection) throws SQLException {
        ArrayList<SaleLine> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM sale_lines ORDER BY position");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) values.add(new SaleLine(rows.getString("receipt_no"), rows.getString("sku"), rows.getString("product_name"),
                    decimal(rows, "unit_price"), rows.getInt("quantity"), decimal(rows, "line_total")));
        }
        return values;
    }

    private static List<StockMovement> readMovements(Connection connection) throws SQLException {
        ArrayList<StockMovement> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM stock_movements ORDER BY position");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) values.add(new StockMovement(rows.getLong("id"), LocalDateTime.parse(rows.getString("occurred_at")),
                    rows.getString("sku"), rows.getString("product_name"), rows.getString("type"), rows.getInt("quantity"),
                    rows.getString("reason"), rows.getString("reference")));
        }
        return values;
    }

    private static List<User> readUsers(Connection connection) throws SQLException {
        ArrayList<User> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT username, salt, password_hash FROM users ORDER BY position");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) values.add(new User(rows.getString("username"), rows.getString("salt"), rows.getString("password_hash")));
        }
        return values;
    }

    private static Settings readSettings(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT store_name, store_address, receipt_footer FROM settings WHERE id = 1");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? new Settings(rows.getString("store_name"), rows.getString("store_address"), rows.getString("receipt_footer")) : null;
        }
    }

    private static BigDecimal decimal(ResultSet rows, String name) throws SQLException {
        return new BigDecimal(rows.getString(name));
    }

    private static void clearLiveData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM products");
            statement.executeUpdate("DELETE FROM sales");
            statement.executeUpdate("DELETE FROM sale_lines");
            statement.executeUpdate("DELETE FROM stock_movements");
            statement.executeUpdate("DELETE FROM users");
            statement.executeUpdate("DELETE FROM settings");
        }
    }

    private static void insertProducts(Connection connection, List<Product> values) throws SQLException {
        String sql = "INSERT INTO products (position, sku, name, category, variant, size_oz, unit, cost_price, selling_price, reorder_level, qty_on_hand, active, image) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                bindProduct(statement, i, values.get(i));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void bindProduct(PreparedStatement statement, int position, Product p) throws SQLException {
        statement.setInt(1, position); statement.setString(2, p.sku()); statement.setString(3, p.name());
        statement.setString(4, p.category()); statement.setString(5, p.variant()); statement.setInt(6, p.sizeOz());
        statement.setString(7, p.unit()); statement.setString(8, p.costPrice().toPlainString());
        statement.setString(9, p.sellingPrice().toPlainString()); statement.setInt(10, p.reorderLevel());
        statement.setInt(11, p.qtyOnHand()); statement.setInt(12, p.active() ? 1 : 0); statement.setString(13, p.image());
    }

    private static void insertSales(Connection connection, List<Sale> values) throws SQLException {
        String sql = "INSERT INTO sales (position, receipt_no, sold_at, cashier, subtotal, discount, total, cash_tendered, change_amount, status, void_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                Sale s = values.get(i); statement.setInt(1, i); statement.setString(2, s.receiptNo());
                statement.setString(3, s.soldAt().toString()); statement.setString(4, s.cashier());
                statement.setString(5, s.subtotal().toPlainString()); statement.setString(6, s.discount().toPlainString());
                statement.setString(7, s.total().toPlainString()); statement.setString(8, s.cashTendered().toPlainString());
                statement.setString(9, s.changeAmount().toPlainString()); statement.setString(10, s.status());
                statement.setString(11, s.voidReason()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertSaleLines(Connection connection, List<SaleLine> values) throws SQLException {
        String sql = "INSERT INTO sale_lines (position, receipt_no, sku, product_name, unit_price, quantity, line_total) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                SaleLine line = values.get(i); statement.setInt(1, i); statement.setString(2, line.receiptNo());
                statement.setString(3, line.sku()); statement.setString(4, line.productName());
                statement.setString(5, line.unitPrice().toPlainString()); statement.setInt(6, line.quantity());
                statement.setString(7, line.lineTotal().toPlainString()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertMovements(Connection connection, List<StockMovement> values) throws SQLException {
        String sql = "INSERT INTO stock_movements (position, id, occurred_at, sku, product_name, type, quantity, reason, reference) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                StockMovement movement = values.get(i); statement.setInt(1, i); statement.setLong(2, movement.id());
                statement.setString(3, movement.occurredAt().toString()); statement.setString(4, movement.sku());
                statement.setString(5, movement.productName()); statement.setString(6, movement.type());
                statement.setInt(7, movement.quantity()); statement.setString(8, movement.reason());
                statement.setString(9, movement.reference()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertUsers(Connection connection, List<User> values) throws SQLException {
        String sql = "INSERT INTO users (position, username, salt, password_hash) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.size(); i++) {
                User user = values.get(i); statement.setInt(1, i); statement.setString(2, user.username());
                statement.setString(3, user.salt()); statement.setString(4, user.passwordHash()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertSettings(Connection connection, Settings settings) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO settings (id, store_name, store_address, receipt_footer) VALUES (1, ?, ?, ?)")) {
            statement.setString(1, settings.storeName()); statement.setString(2, settings.storeAddress());
            statement.setString(3, settings.receiptFooter()); statement.executeUpdate();
        }
    }

    private <T> T transaction(SqlWork<T> work) throws IOException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | IOException | RuntimeException e) {
                rollback(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw storageError("Could not update the local database", e);
        }
    }

    private static void rollback(Connection connection, Exception failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private boolean hasLegacyFiles() {
        for (String file : LEGACY_FILES) if (Files.exists(DATA_DIRECTORY.resolve(file))) return true;
        return false;
    }

    private void importLegacyFiles(Connection connection) throws IOException, SQLException {
        Path path = DATA_DIRECTORY.resolve("products.csv");
        if (Files.exists(path)) {
            ArrayList<Product> values = new ArrayList<>();
            for (List<String> row : legacyRows(path, PRODUCT_COLUMNS)) values.add(new Product(row.get(0), row.get(1), row.get(2), row.get(3),
                    Integer.parseInt(row.get(4)), row.get(5), new BigDecimal(row.get(6)), new BigDecimal(row.get(7)),
                    Integer.parseInt(row.get(8)), Integer.parseInt(row.get(9)), Boolean.parseBoolean(row.get(10)), row.get(11)));
            try (Statement statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM products"); }
            insertProducts(connection, values);
        }
        path = DATA_DIRECTORY.resolve("sales.csv");
        if (Files.exists(path)) {
            ArrayList<Sale> values = new ArrayList<>();
            for (List<String> row : legacyRows(path, SALE_COLUMNS)) values.add(new Sale(row.get(0), LocalDateTime.parse(row.get(1)), row.get(2),
                    new BigDecimal(row.get(3)), new BigDecimal(row.get(4)), new BigDecimal(row.get(5)), new BigDecimal(row.get(6)),
                    new BigDecimal(row.get(7)), row.get(8), row.get(9)));
            try (Statement statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM sales"); }
            insertSales(connection, values);
        }
        path = DATA_DIRECTORY.resolve("sale_lines.csv");
        if (Files.exists(path)) {
            ArrayList<SaleLine> values = new ArrayList<>();
            for (List<String> row : legacyRows(path, SALE_LINE_COLUMNS)) values.add(new SaleLine(row.get(0), row.get(1), row.get(2),
                    new BigDecimal(row.get(3)), Integer.parseInt(row.get(4)), new BigDecimal(row.get(5))));
            try (Statement statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM sale_lines"); }
            insertSaleLines(connection, values);
        }
        path = DATA_DIRECTORY.resolve("stock_movements.csv");
        if (Files.exists(path)) {
            ArrayList<StockMovement> values = new ArrayList<>();
            for (List<String> row : legacyRows(path, MOVEMENT_COLUMNS)) values.add(new StockMovement(Long.parseLong(row.get(0)),
                    LocalDateTime.parse(row.get(1)), row.get(2), row.get(3), row.get(4), Integer.parseInt(row.get(5)), row.get(6), row.get(7)));
            try (Statement statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM stock_movements"); }
            insertMovements(connection, values);
        }
        path = DATA_DIRECTORY.resolve("users.csv");
        if (Files.exists(path)) {
            ArrayList<User> values = new ArrayList<>();
            for (List<String> row : legacyRows(path, USER_COLUMNS)) values.add(new User(row.get(0), row.get(1), row.get(2)));
            try (Statement statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM users"); }
            insertUsers(connection, values);
        }
        path = DATA_DIRECTORY.resolve("settings.csv");
        if (Files.exists(path)) {
            List<List<String>> rows = legacyRows(path, SETTINGS_COLUMNS);
            if (rows.size() != 1) throw new IOException("settings.csv must contain exactly one settings record.");
            List<String> row = rows.get(0);
            try (Statement statement = connection.createStatement()) { statement.executeUpdate("DELETE FROM settings"); }
            insertSettings(connection, new Settings(row.get(0), row.get(1), row.get(2)));
        }
    }

    private static List<List<String>> legacyRows(Path file, List<String> expectedHeader) throws IOException {
        List<List<String>> rows = parseCsv(Files.readString(file, StandardCharsets.UTF_8));
        if (rows.isEmpty() || !rows.get(0).equals(expectedHeader)) throw new IOException("Unexpected CSV layout in " + file.getFileName());
        ArrayList<List<String>> records = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).size() != expectedHeader.size()) throw new IOException("Invalid record in " + file.getFileName());
            records.add(rows.get(i));
        }
        return records;
    }

    private static List<List<String>> parseCsv(String text) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false;
                else field.append(c);
            } else if (c == '"' && field.isEmpty()) quoted = true;
            else if (c == ',') { row.add(field.toString()); field.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else field.append(c);
        }
        if (quoted) throw new IOException("Unclosed quoted field in a legacy CSV file.");
        if (!row.isEmpty() || !field.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    private static void removeLegacyFiles() {
        for (String file : LEGACY_FILES) {
            try {
                Files.deleteIfExists(DATA_DIRECTORY.resolve(file));
            } catch (IOException e) {
                System.err.println("Could not remove migrated " + file + ": " + e.getMessage());
            }
        }
    }

    private static IOException storageError(String message, SQLException cause) {
        return new IOException(message + ": " + cause.getMessage(), cause);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException, IOException;
    }
}
