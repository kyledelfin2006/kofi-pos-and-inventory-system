package com.kofi.pos.data.csv; // CSV layer: everything that knows file names, columns and row layouts.

import java.util.List;             // Row and column lists.
import java.util.function.Function; // Row <-> record converters.

/**
 * Describes ONE csv file: its name, its column names (the header line), and how to convert
 * a row of text into a record and back. Each file has a small class that builds one of these.
 *
 * @param <T> the record type stored in the file
 */
public final class CsvTable<T> {
    private final String file;                       // File name inside the data folder, e.g. "products.csv".
    private final List<String> columns;              // Header names, in column order.
    private final Function<List<String>, T> reader;  // Row of text -> record.
    private final Function<T, List<String>> writer;  // Record -> row of text.

    public CsvTable(String file, List<String> columns, Function<List<String>, T> reader, Function<T, List<String>> writer) {
        this.file = file;       // Remember each part.
        this.columns = columns;
        this.reader = reader;
        this.writer = writer;
    }

    public String file() { return file; }                  // File name.
    public List<String> columns() { return columns; }      // Expected header.
    public T fromRow(List<String> row) { return reader.apply(row); }  // Text row -> record.
    public List<String> toRow(T value) { return writer.apply(value); } // Record -> text row.
}
