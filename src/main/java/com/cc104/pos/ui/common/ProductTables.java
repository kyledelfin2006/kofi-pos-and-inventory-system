package com.cc104.pos.ui.common; // Shared UI helpers.

import com.cc104.pos.model.Product;                   // Row type of the table.
import javafx.beans.property.ReadOnlyIntegerWrapper;  // Wraps an int so a table cell can display it.
import javafx.beans.property.ReadOnlyStringWrapper;   // Wraps a String for a table cell.
import javafx.scene.control.Label;                    // Placeholder text.
import javafx.scene.control.TableColumn;              // One column.
import javafx.scene.control.TableView;                // The table widget.

import java.util.List; // Used to add several columns at once.

/** Builds the shared product table used by checkout and product maintenance. */
public final class ProductTables {
    private ProductTables() { } // Utility class.

    /** Creates an empty product table; {@code showActive} adds an Active/Inactive column. */
    public static TableView<Product> create(boolean showActive) {
        TableView<Product> table = new TableView<>();
        // Each column gets a "cell value factory": a function that extracts what to show from a row's Product.
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
        table.getColumns().addAll(List.of(sku, name, category, price, quantity)); // Attach the columns in order.

        if (showActive) { // Optional extra column for the maintenance screen.
            TableColumn<Product, String> status = new TableColumn<>("Status");
            status.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().active() ? "Active" : "Inactive"));
            table.getColumns().add(status);
        }

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); // Columns fill the width.
        table.setPlaceholder(new Label("No products match this search."));                  // Shown when empty.
        table.setSortPolicy(ignored -> false); // Disable JavaFX's built-in sorting: our own algorithms decide the order.
        return table;
    }
}
