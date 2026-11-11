package com.cc104.pos.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Low-level local file store for UTF-8 CSV data and packaged seed files. */
public final class CsvStore {
    private final Path directory = Path.of("data");

    /** Creates the data directory and copies only seed files that do not exist yet. */
    public void initialize() throws IOException {
        Files.createDirectories(directory);
        for (String file : List.of("products.csv", "users.csv", "sales.csv", "sale_lines.csv",
                "stock_movements.csv", "settings.csv")) {
            Path target = directory.resolve(file);
            if (Files.exists(target)) continue;
            try (InputStream seed = CsvStore.class.getResourceAsStream("/seed/" + file)) {
                if (seed == null) throw new IOException("Missing starter file: " + file);
                Files.copy(seed, target);
            }
        }
    }

    /** Reads rows while supporting quoted commas, escaped quotes, and embedded line breaks. */
    public List<List<String>> read(String file) throws IOException {
        String text = Files.readString(directory.resolve(file), StandardCharsets.UTF_8);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (quoted) throw new IOException("Unclosed quoted field in " + file);
        if (!row.isEmpty() || !field.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /** Replaces a CSV file through a temporary sibling to avoid truncated writes. */
    public void write(String file, List<String> header, List<List<String>> records) throws IOException {
        StringBuilder csv = new StringBuilder();
        appendRow(csv, header);
        for (List<String> record : records) appendRow(csv, record);

        Path target = directory.resolve(file);
        Path temporary = Files.createTempFile(directory, file, ".tmp");
        try {
            Files.writeString(temporary, csv, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void appendRow(StringBuilder output, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) output.append(',');
            String value = fields.get(i) == null ? "" : fields.get(i);
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                output.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                output.append(value);
            }
        }
        output.append(System.lineSeparator());
    }
}
