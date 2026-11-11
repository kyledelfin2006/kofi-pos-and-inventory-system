package com.kofi.pos.ui.view; // One class per screen.

import com.kofi.pos.model.Product;                   // Product model.
import com.kofi.pos.model.StockMovement;             // Movement model.
import com.kofi.pos.service.PosService;              // Business operations.
import javafx.beans.property.ReadOnlyIntegerWrapper;  // Number cell value.
import javafx.beans.property.ReadOnlyStringWrapper;   // Text cell value.
import javafx.collections.FXCollections;              // Observable lists.
import javafx.geometry.Insets;                        // Padding.
import javafx.scene.Node;                             // Base visual type.
import javafx.scene.control.Button;                   // Button.
import javafx.scene.control.ComboBox;                 // Drop-down.
import javafx.scene.control.Label;                    // Text.
import javafx.scene.control.TableColumn;              // Column.
import javafx.scene.control.TableView;                // Table.
import javafx.scene.control.TextArea;                 // Multi-line input.
import javafx.scene.control.TextField;                // Input.
import javafx.scene.input.KeyCode;                    // Keys.
import javafx.scene.layout.HBox;                      // Horizontal layout.
import javafx.scene.layout.Priority;                  // Grow priority.
import javafx.scene.layout.VBox;                      // Vertical layout.
import javafx.util.StringConverter;                   // Converts Product <-> text in the combo box.
import java.io.IOException;                           // Storage failure.
import java.util.ArrayList;                           // Lists.
import java.util.List;                                // List.of.
import java.util.function.Consumer;                   // Callback.
import static com.kofi.pos.ui.common.UiSupport.*;    // Shared helpers.

/** Builds the manual stock movement form and movement history. */
public final class StockView {
    private final PosService service;          // Business operations.
    private final String pendingSku;           // SKU preselected when arriving from the restock queue (may be null).
    private final Runnable clearPendingStock;  // Tells the shell the preselection was consumed.
    private final Consumer<String> notice;     // Top-bar message callback.
    private final Consumer<String> navigate;   // Opens a module by name.

    public StockView(PosService service, String pendingSku, Runnable clearPendingStock,
                     Consumer<String> notice, Consumer<String> navigate) {
        this.service = service;
        this.pendingSku = pendingSku;
        this.clearPendingStock = clearPendingStock;
        this.notice = notice;
        this.navigate = navigate;
    }

    /** Creates the screen's visual tree. */
    public Node build() {
        VBox root = page("Stock movements", "Record stock in, stock out, and downward adjustments. Every change is saved to the local movement log.");
        VBox form = stockForm(); // Left: the entry form.
        TableView<StockMovement> history = new TableView<>(); // Right: audit log table.
        TableColumn<StockMovement, String> time = new TableColumn<>("Date & time");
        time.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().occurredAt().format(DATE_TIME)));
        TableColumn<StockMovement, String> product = new TableColumn<>("Product");
        product.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().productName()));
        TableColumn<StockMovement, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().type()));
        TableColumn<StockMovement, Number> quantity = new TableColumn<>("Qty");
        quantity.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().quantity()));
        TableColumn<StockMovement, String> reference = new TableColumn<>("Reference");
        reference.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().reference()));
        TableColumn<StockMovement, String> note = new TableColumn<>("Reason / note");
        note.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().reason()));
        history.getColumns().addAll(List.of(time, product, type, quantity, reference, note));
        ArrayList<StockMovement> recent = new ArrayList<>(service.movements());
        for (int i = 0, j = recent.size() - 1; i < j; i++, j--) { // Reverse in place (two-pointer swap) so newest shows first.
            StockMovement temp = recent.get(i); recent.set(i, recent.get(j)); recent.set(j, temp);
        }
        history.setItems(FXCollections.observableArrayList(recent));
        history.setPrefHeight(620);
        history.setPlaceholder(new Label("Stock changes will appear here."));
        VBox log = panel("Movement history", history);
        HBox body = new HBox(18, form, log);
        form.setPrefWidth(350);
        HBox.setHgrow(log, Priority.ALWAYS);
        root.getChildren().add(body);
        return root;
    }

    /** Builds the "New movement" form with live validation. */
    private VBox stockForm() {
        Label title = new Label("New movement");
        title.getStyleClass().add("section-heading");
        TextField productSearch = new TextField(); // Filters the product combo box.
        productSearch.setPromptText("Search active products");
        productSearch.setTextFormatter(limitFormatter(60));
        trimOnBlur(productSearch);
        ComboBox<Product> product = new ComboBox<>(FXCollections.observableArrayList(service.activeProducts()));
        product.setPromptText("Select a product");
        product.setConverter(new StringConverter<>() { // How a Product is shown / parsed in the combo box.
            @Override public String toString(Product value) { return value == null ? "" : value.sku() + " · " + value.displayName(); }
            @Override public Product fromString(String value) {
                if (value == null) return null;
                for (Product candidate : service.activeProducts()) if (candidate.sku().equalsIgnoreCase(value.trim())
                        || candidate.displayName().equalsIgnoreCase(value.trim())) return candidate;
                return null;
            }
        });
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("Stock In", "Stock Out", "Adjust"));
        type.getSelectionModel().selectFirst();
        TextField quantity = new TextField();
        quantity.setTextFormatter(integerFormatter()); // Digits only.
        TextArea reason = new TextArea();
        reason.setWrapText(true);
        reason.setPrefRowCount(4);
        reason.setTextFormatter(limitFormatter(200));
        Label reasonCount = counter(reason, 200);
        TextField reference = new TextField();
        reference.setTextFormatter(limitFormatter(50));
        trimOnBlur(reference);
        Label error = errorLabel();
        Button save = new Button("Save movement");
        save.getStyleClass().add("primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setDefaultButton(true);
        VBox box = new VBox(10, title, field("Find product", productSearch), field("Product *", product), field("Movement type *", type),
                field("Quantity *", quantity), field("Reason / note", reason), reasonCount,
                field("Reference", reference), error, save);
        box.getStyleClass().add("surface-panel");
        box.setPadding(new Insets(18));
        if (pendingSku != null) { // Arrived from "Receive next restock": preselect that product.
            Product next = service.findProduct(pendingSku);
            if (next != null) product.setValue(next);
            clearPendingStock.run();
        }
        productSearch.textProperty().addListener((obs, old, value) -> {
            ArrayList<Product> matches = new ArrayList<>();
            for (Product candidate : service.searchProducts(value)) matches.add(candidate); // Linear search.
            product.setItems(FXCollections.observableArrayList(matches));
            product.setValue(null);
            if (!value.isBlank()) product.show(); // Pop the list open.
        });
        Runnable validateQuantity = () -> { // Re-validates the whole form after any change.
            Product selected = product.getValue();
            String entered = quantity.getText();
            String message = "";
            boolean quantityInvalid = entered.isBlank();
            boolean noteRequired = type.getValue() != null && !type.getValue().equals("Stock In"); // Out/Adjust need a note.
            boolean noteInvalid = noteRequired && reason.getText().trim().isEmpty();
            if (selected == null) message = "Select an active product.";
            else if (type.getValue() == null) message = "Choose a movement type.";
            else if (entered.isBlank()) message = "Quantity is required.";
            else {
                try {
                    int qty = Integer.parseInt(entered);
                    quantityInvalid = qty <= 0 || (!type.getValue().equals("Stock In") && qty > selected.qtyOnHand());
                    if (qty <= 0) message = "Quantity must be greater than zero.";
                    else if (!type.getValue().equals("Stock In") && qty > selected.qtyOnHand()) message = "Only " + selected.qtyOnHand() + " in stock";
                    else if (noteInvalid) message = "A note is required for Stock Out and Adjust.";
                } catch (NumberFormatException ignored) { message = "Enter a whole-number quantity."; }
            }
            if (message.isEmpty() && noteInvalid) message = "A note is required for Stock Out and Adjust.";
            error.setText(message);
            setInvalid(product, selected == null);
            setInvalid(type, type.getValue() == null);
            setInvalid(quantity, quantityInvalid || selected == null);
            setInvalid(reason, noteInvalid);
            save.setDisable(!error.getText().isEmpty()); // Save only when no error message remains.
        };
        type.valueProperty().addListener((obs, old, value) -> {
            boolean required = value != null && !value.equals("Stock In");
            setRequiredLabel(box, "Reason / note", required); // Add/remove the * marker.
            validateQuantity.run();
        });
        quantity.textProperty().addListener((obs, old, value) -> validateQuantity.run());
        product.valueProperty().addListener((obs, old, value) -> validateQuantity.run());
        reason.textProperty().addListener((obs, old, value) -> validateQuantity.run());
        reason.focusedProperty().addListener((obs, was, focused) -> { if (!focused) reason.setText(reason.getText().trim()); });
        reference.focusedProperty().addListener((obs, was, focused) -> { if (!focused) reference.setText(reference.getText().trim()); });
        validateQuantity.run();
        save.setOnAction(event -> {
            try {
                service.moveStock(product.getValue().sku(), type.getValue(), Integer.parseInt(quantity.getText()), reason.getText(), reference.getText());
                notice.accept("Stock movement saved");
                navigate.accept("Stock"); // Reload the screen with the new row.
            } catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
        });
        box.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) { // Esc clears the whole form.
                productSearch.clear();
                product.setValue(null);
                quantity.clear();
                reason.clear();
                reference.clear();
                error.setText("");
            }
        });
        return box;
    }
}
