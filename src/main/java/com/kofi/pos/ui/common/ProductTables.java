package com.kofi.pos.ui.common; // Shared UI helpers.

import com.kofi.pos.model.Product;                   // Row type of the table.
import javafx.beans.property.ReadOnlyIntegerWrapper;  // Wraps an int so a table cell can display it.
import javafx.beans.property.ReadOnlyStringWrapper;   // Wraps a String for a table cell.
import javafx.scene.control.Label;                    // Placeholder text.
import javafx.scene.control.TableCell;                // Custom cell (used for the picture).
import javafx.scene.image.ImageView;                  // Shows the thumbnail.
import javafx.scene.control.TableColumn;              // One column.
import javafx.scene.control.TableView;                // The table widget.

import java.util.List; // Used to add several columns at once.
import java.util.function.Function; // Finds the product behind a table row.

/** Builds the shared product table used by checkout and product maintenance. */
public final class ProductTables {
    private ProductTables() { } // Utility class.

    /** Creates an empty product table; {@code showActive} adds an Active/Inactive column. */
    public static TableView<Product> create(boolean showActive) {
        TableView<Product> table = new TableView<>();
        // Each column gets a "cell value factory": a function that extracts what to show from a row's Product.
        TableColumn<Product, Product> picture = new TableColumn<>(""); // Thumbnail column.
        picture.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue()));
        picture.setCellFactory(column -> new TableCell<>() {
            private final ImageView view = AssetLoader.icon(null, 32); // One reusable rounded view per cell.
            @Override protected void updateItem(Product product, boolean empty) {
                super.updateItem(product, empty);
                if (empty || product == null) { setGraphic(null); return; }
                view.setImage(AssetLoader.product(product, 64)); // Own picture or category placeholder.
                setGraphic(view);
            }
        });
        picture.setMaxWidth(52); picture.setMinWidth(52);
        TableColumn<Product, String> sku = new TableColumn<>("SKU");
        sku.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().sku()));
        TableColumn<Product, String> name = new TableColumn<>("Product");
        name.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().displayName()));
        TableColumn<Product, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().category()));
        TableColumn<Product, String> price = new TableColumn<>("Price");
        price.setCellValueFactory(cell -> new ReadOnlyStringWrapper(UiSupport.peso(cell.getValue().sellingPrice())));
        TableColumn<Product, Number> quantity = new TableColumn<>("On hand");
        quantity.setCellValueFactory(cell -> new ReadOnlyIntegerWrapper(cell.getValue().qtyOnHand()));
        table.getColumns().addAll(List.of(picture, sku, name, category, price, quantity)); // Attach the columns in order.

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

    /** A text cell with the row's product icon in front of the text. Rows whose product is gone show text only. */
    public static <S> TableCell<S, String> iconCell(Function<S, Product> productOf) {
        return new TableCell<>() {
            private final ImageView view = AssetLoader.icon(null, 28); // One reusable rounded icon per cell.
            @Override protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                S row = getTableRow() == null ? null : getTableRow().getItem();
                Product product = empty || row == null ? null : productOf.apply(row);
                setText(empty ? null : text);
                if (product == null) { setGraphic(null); return; }
                view.setImage(AssetLoader.product(product, 56)); // Rendered at twice the size so it stays sharp on high-DPI screens.
                setGraphic(view);
                setGraphicTextGap(10);
            }
        };
    }
}
