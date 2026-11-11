package com.cc104.pos.data; // Persistence layer.

import com.cc104.pos.model.Product;       // Catalogue record.
import com.cc104.pos.model.Sale;          // Receipt header record.
import com.cc104.pos.model.SaleLine;      // Receipt line record.
import com.cc104.pos.model.Settings;      // Store settings record.
import com.cc104.pos.model.StockMovement; // Stock audit record.
import com.cc104.pos.model.User;          // Login record.

import java.util.List; // Generic list interface.

/** Immutable snapshot exchanged between the application service and storage. */
public record StoreData(List<Product> products, List<Sale> sales, List<SaleLine> saleLines,
                        List<StockMovement> movements, List<User> users, Settings settings) {
    /** Compact constructor: defensively copies each list so the snapshot can never change afterwards. */
    public StoreData {
        products = List.copyOf(products);     // Unmodifiable copy of the products.
        sales = List.copyOf(sales);           // Unmodifiable copy of the sales.
        saleLines = List.copyOf(saleLines);   // Unmodifiable copy of the sale lines.
        movements = List.copyOf(movements);   // Unmodifiable copy of the movements.
        users = List.copyOf(users);           // Unmodifiable copy of the users.
    }
}
