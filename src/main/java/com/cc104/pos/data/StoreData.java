package com.cc104.pos.data;

import com.cc104.pos.model.Activity;
import com.cc104.pos.model.Product;
import com.cc104.pos.model.Sale;
import com.cc104.pos.model.SaleLine;
import com.cc104.pos.model.Settings;
import com.cc104.pos.model.StockMovement;
import com.cc104.pos.model.User;

import java.util.List;

/** Immutable snapshot exchanged between the application service and storage. */
public record StoreData(List<Product> products, List<Sale> sales, List<SaleLine> saleLines,
                        List<StockMovement> movements, List<User> users, Settings settings) {
    public StoreData {
        products = List.copyOf(products);
        sales = List.copyOf(sales);
        saleLines = List.copyOf(saleLines);
        movements = List.copyOf(movements);
        users = List.copyOf(users);
    }
}
