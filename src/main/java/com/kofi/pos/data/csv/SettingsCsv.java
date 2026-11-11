package com.kofi.pos.data.csv; // CSV layer.

import com.kofi.pos.model.Settings; // Record stored in settings.csv (one data row).

import java.util.List; // Column list.

/** Layout of settings.csv. */
public final class SettingsCsv {
    private SettingsCsv() { } // Holder class.

    public static final CsvTable<Settings> TABLE = new CsvTable<>("settings.csv",
            List.of("store_name", "store_address", "receipt_footer"),
            row -> new Settings(row.get(0), row.get(1), row.get(2)),
            s -> List.of(s.storeName(), s.storeAddress(), s.receiptFooter()));
}
