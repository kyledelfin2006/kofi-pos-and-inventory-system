package com.cc104.pos.data; // Persistence layer: the only package that knows how data is stored.

import java.io.IOException; // Thrown when storage cannot be read or written.

/** Persistence boundary used by the POS business service (the "port" in ports-and-adapters). */
public interface PosRepository {
    /** Reads the whole store (creating it from the default data on first run). */
    StoreData load() throws IOException;

    /** Writes a full snapshot of the store back to storage. */
    void save(StoreData data) throws IOException;

    /**
     * Throws away the current stored data, restores the packaged default data,
     * and returns the freshly restored snapshot.
     */
    StoreData resetToDefaults() throws IOException;
}
