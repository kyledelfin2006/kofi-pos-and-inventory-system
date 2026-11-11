package com.kofi.pos.data; // Persistence layer.

import com.kofi.pos.data.csv.CsvStore;          // File mechanics (read, write, defaults).
import com.kofi.pos.data.csv.CsvTable;          // Per-file layout description.
import com.kofi.pos.data.csv.ProductCsv;        // products.csv layout.
import com.kofi.pos.data.csv.SaleCsv;           // sales.csv layout.
import com.kofi.pos.data.csv.SaleLineCsv;       // sale_lines.csv layout.
import com.kofi.pos.data.csv.SettingsCsv;       // settings.csv layout.
import com.kofi.pos.data.csv.StockMovementCsv;  // stock_movements.csv layout.
import com.kofi.pos.data.csv.UserCsv;           // users.csv layout.
import com.kofi.pos.model.Settings;             // Store settings record.

import java.io.IOException;  // Storage failure.
import java.util.ArrayList;  // Resizable list.
import java.util.List;       // Generic list interface.

/** Reads and writes the application's domain records using the local CSV files. */
public final class CsvPosRepository implements PosRepository {
    /** Every table the app stores; used for the header (schema) check. */
    private static final List<CsvTable<?>> TABLES = List.of(ProductCsv.TABLE, UserCsv.TABLE, SaleCsv.TABLE,
            SaleLineCsv.TABLE, StockMovementCsv.TABLE, SettingsCsv.TABLE);

    private final CsvStore csv; // Low-level file helper.

    public CsvPosRepository(CsvStore csv) {
        this.csv = csv;
    }

    /** Loads everything; creates missing files from the defaults and auto-resets files from an older layout. */
    @Override
    public StoreData load() throws IOException {
        csv.initialize();                       // Create the data folder and any missing file.
        if (!schemaMatches()) {                 // Files from an older version of the app?
            System.err.println("Data files use an older layout; restoring default data.");
            csv.restoreDefaults();              // Auto-reset everything to the current defaults.
        }
        return readSnapshot();
    }

    /** RESET: restore the default CSV files, then read them back as the new snapshot. */
    @Override
    public StoreData resetToDefaults() throws IOException {
        csv.restoreDefaults();
        return readSnapshot();
    }

    /** True when every file's first line equals the column names this version expects. */
    private boolean schemaMatches() {
        for (CsvTable<?> table : TABLES) {
            try {
                List<List<String>> rows = csv.read(table.file());
                if (rows.isEmpty() || !rows.get(0).equals(table.columns())) return false; // Header differs.
            } catch (IOException | RuntimeException e) {
                return false; // Unreadable file counts as a mismatch.
            }
        }
        return true;
    }

    /** Converts every CSV row into a typed record. */
    private StoreData readSnapshot() throws IOException {
        List<Settings> settings = read(SettingsCsv.TABLE); // Settings has exactly one data row.
        if (settings.isEmpty()) throw new IOException("settings.csv has no store settings record.");
        return new StoreData(read(ProductCsv.TABLE), read(SaleCsv.TABLE), read(SaleLineCsv.TABLE),
                read(StockMovementCsv.TABLE), read(UserCsv.TABLE), settings.get(0));
    }

    /** Writes every collection back to its CSV file. */
    @Override
    public void save(StoreData data) throws IOException {
        write(ProductCsv.TABLE, data.products());
        write(SaleCsv.TABLE, data.sales());
        write(SaleLineCsv.TABLE, data.saleLines());
        write(StockMovementCsv.TABLE, data.movements());
        write(SettingsCsv.TABLE, List.of(data.settings()));
    }

    /** Reads a table: skips the header row and converts each remaining row with the table's reader. */
    private <T> List<T> read(CsvTable<T> table) throws IOException {
        List<List<String>> rows = csv.read(table.file());
        ArrayList<T> values = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) values.add(table.fromRow(rows.get(index)));
        return values;
    }

    /** Writes a table: header from the table, one row per record. */
    private <T> void write(CsvTable<T> table, List<T> values) throws IOException {
        ArrayList<List<String>> rows = new ArrayList<>(values.size());
        for (T value : values) rows.add(table.toRow(value));
        csv.write(table.file(), table.columns(), rows);
    }
}
