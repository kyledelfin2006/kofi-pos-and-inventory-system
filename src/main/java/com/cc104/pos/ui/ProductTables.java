package com.cc104.pos.ui;

import com.cc104.pos.model.Product;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

/** Builds the shared product table used by checkout and product maintenance. */
final class ProductTables {
    private ProductTables() { }

    static TableView<Product> create(boolean showActive) {
        TableView<Product> table = new TableView<>();
        TableColumn<Product, String> sku = new TableColumn<>("SKU");
        sku.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().sku()));
        TableColumn<Product, String> name = new TableColumn<>("Product");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        TableColumn<Product, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().category()));
        TableColumn<Product, String> price = new TableColumn<>("Price");
        price.setCellValueFactory(cell -> new ReadOnlyStringWrapper(UiSupport.peso(cell.getValue().sellingPrice())));
        TableColumn<Product, Number> quantity = new TableColumn<>("On hand");
        quantity.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().qtyOnHand()));
        table.getColumns().addAll(List.of(sku, name, category, price, quantity));

        if (showActive) {
            TableColumn<Product, String> status = new TableColumn<>("Status");
            status.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().active() ? "Active" : "Inactive"));
            table.getColumns().add(status);
        }

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No products match this search."));
        table.setSortPolicy(ignored -> false);
        return table;
    }
}
