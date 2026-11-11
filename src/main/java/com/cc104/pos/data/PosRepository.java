package com.cc104.pos.data;

import java.io.IOException;

/** Persistence boundary used by the POS business service. */
public interface PosRepository {
    StoreData load() throws IOException;

    void save(StoreData data) throws IOException;
}
