package com.kofi.pos.data.csv; // Persistence layer.

import java.io.IOException;                          // Signals file problems.
import java.io.InputStream;                          // Used to read the packaged default (seed) files.
import java.nio.charset.StandardCharsets;           // UTF-8 constant.
import java.nio.file.AtomicMoveNotSupportedException; // Thrown if the file system cannot do an atomic rename.
import java.nio.file.Files;                          // File helper methods.
import java.nio.file.Path;                           // A file or folder location.
import java.nio.file.StandardCopyOption;             // Options such as REPLACE_EXISTING.
import java.util.ArrayList;                          // Resizable list.
import java.util.List;                               // Generic list interface.

/**
 * Low-level local file store for UTF-8 CSV data and the packaged default (seed) files.
 * The seed files in {@code src/main/resources/seed} ARE the "default data" used by Reset.
 */
public final class CsvStore {
    /** Every CSV file the app uses; each has a matching default copy under /seed in the jar. */
    private static final List<String> FILES = List.of("products.csv", "users.csv", "sales.csv",
            "sale_lines.csv", "stock_movements.csv", "settings.csv");

    private final Path directory = Path.of("data"); // Working data folder, next to where the app is launched.

    /** Creates the data directory and copies only the default files that do not exist yet. */
    public void initialize() throws IOException {
        Files.createDirectories(directory); // No error if it already exists.
        for (String file : FILES) {         // Check each of the six files.
            Path target = directory.resolve(file); // Where the live copy should be.
            if (Files.exists(target)) continue;    // Keep the user's existing data untouched.
            copyDefault(file, target, false);      // Otherwise create it from the default.
        }
    }

    /** RESET: overwrites every live CSV with its packaged default, discarding all current records. */
    public void restoreDefaults() throws IOException {
        Files.createDirectories(directory); // Make sure the folder exists (it might have been deleted).
        for (String file : FILES) {
            copyDefault(file, directory.resolve(file), true); // true = overwrite what is there.
        }
    }

    /** Copies one default file out of the application resources into the data folder. */
    private void copyDefault(String file, Path target, boolean replace) throws IOException {
        try (InputStream seed = CsvStore.class.getResourceAsStream("/seed/" + file)) { // Open the packaged default.
            if (seed == null) throw new IOException("Missing starter file: " + file);   // Packaging mistake guard.
            if (replace) Files.copy(seed, target, StandardCopyOption.REPLACE_EXISTING);  // Overwrite for reset.
            else Files.copy(seed, target);                                               // Plain copy for first run.
        } // try-with-resources closes the stream automatically.
    }

    /** Reads rows while supporting quoted commas, escaped quotes, and embedded line breaks. */
    public List<List<String>> read(String file) throws IOException {
        String text = Files.readString(directory.resolve(file), StandardCharsets.UTF_8); // Whole file as one string.
        List<List<String>> rows = new ArrayList<>(); // Finished rows.
        List<String> row = new ArrayList<>();        // The row being built.
        StringBuilder field = new StringBuilder();   // The field being built.
        boolean quoted = false;                      // True while we are inside "..." quotes.

        for (int i = 0; i < text.length(); i++) { // Walk the text one character at a time (a tiny state machine).
            char c = text.charAt(i);              // Current character.
            if (quoted) {                         // --- State: inside quotes ---
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"'); // Two quotes in a row mean one literal quote character.
                    i++;               // Skip the second quote.
                } else if (c == '"') {
                    quoted = false;    // A single quote closes the quoted section.
                } else {
                    field.append(c);   // Anything else (commas, newlines) is plain text inside quotes.
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;                      // A quote at the start of a field opens a quoted section.
            } else if (c == ',') {
                row.add(field.toString());          // Comma outside quotes ends the field.
                field.setLength(0);                 // Start a new empty field.
            } else if (c == '\n' || c == '\r') {    // Line break outside quotes ends the row.
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++; // Treat \r\n as one break.
                row.add(field.toString());          // Finish the last field of the row.
                field.setLength(0);                 // Reset the field buffer.
                if (!(row.size() == 1 && row.get(0).isEmpty())) rows.add(row); // Skip completely blank lines.
                row = new ArrayList<>();            // Start a fresh row.
            } else {
                field.append(c);                    // Ordinary character: add to the current field.
            }
        }
        if (quoted) throw new IOException("Unclosed quoted field in " + file); // File ended inside quotes: corrupt.
        if (!row.isEmpty() || !field.isEmpty()) {   // The file did not end with a newline, so flush the last row.
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    /** Replaces a CSV file through a temporary sibling to avoid truncated writes. */
    public void write(String file, List<String> header, List<List<String>> records) throws IOException {
        StringBuilder csv = new StringBuilder(); // Build the whole file text in memory first.
        appendRow(csv, header);                  // First line = column names.
        for (List<String> record : records) appendRow(csv, record); // One line per record.

        Path target = directory.resolve(file);                          // Final file location.
        Path temporary = Files.createTempFile(directory, file, ".tmp"); // Scratch file in the same folder.
        try {
            Files.writeString(temporary, csv, StandardCharsets.UTF_8);  // Write everything to the scratch file.
            try {
                // Swap the scratch file into place in one atomic step (readers never see half a file).
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                // Some file systems cannot do atomic moves; fall back to a normal replace.
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary); // Clean up the scratch file if anything went wrong.
        }
    }

    /** Appends one CSV line, quoting any field that contains a comma, quote, or line break. */
    private static void appendRow(StringBuilder output, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) output.append(',');                                  // Separator between fields.
            String value = fields.get(i) == null ? "" : fields.get(i);      // Null becomes empty text.
            if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                output.append('"').append(value.replace("\"", "\"\"")).append('"'); // Wrap in quotes, double inner quotes.
            } else {
                output.append(value);                                       // Safe to write as-is.
            }
        }
        output.append(System.lineSeparator()); // End of this record.
    }
}
