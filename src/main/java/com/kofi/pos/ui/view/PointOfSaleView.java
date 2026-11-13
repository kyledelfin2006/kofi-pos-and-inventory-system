package com.kofi.pos.ui.view; // One class per screen.

import com.kofi.pos.model.CartLine;                  // Cart line model.
import com.kofi.pos.model.Product;                   // Product model.
import com.kofi.pos.model.Sale;                      // Completed sale.
import com.kofi.pos.service.PosService;              // Business operations.
import com.kofi.pos.ui.common.ProductTables;         // Icon cell for the cart.
import com.kofi.pos.ui.common.ProductTiles;          // Product picture grid.
import javafx.animation.PauseTransition;              // Timer used to debounce typing.
import javafx.beans.property.ReadOnlyIntegerWrapper;  // Number cell value.
import javafx.beans.property.ReadOnlyStringWrapper;   // Text cell value.
import javafx.collections.FXCollections;              // Observable lists.
import javafx.geometry.Insets;                        // Padding.
import javafx.scene.Node;                             // Base visual type.
import javafx.scene.control.Button;                   // Button.
import javafx.scene.control.Label;                    // Text.
import javafx.scene.control.ScrollPane;               // Scrolling tile area.
import javafx.scene.control.TableCell;                // Custom table cell.
import javafx.scene.control.TableColumn;              // Column.
import javafx.scene.control.TableView;                // Table.
import javafx.scene.control.TextField;                // Input.
import javafx.scene.control.ToggleButton;             // Category chip.
import javafx.scene.control.ToggleGroup;              // One chip selected at a time.
import javafx.scene.layout.FlowPane;                  // Tile grid type.
import javafx.scene.layout.ColumnConstraints;         // Grid column sizing.
import javafx.scene.layout.GridPane;                  // Grid layout.
import javafx.scene.layout.HBox;                      // Horizontal layout.
import javafx.scene.layout.Priority;                  // Grow priority.
import javafx.scene.layout.VBox;                      // Vertical layout.
import javafx.util.Duration;                          // Time span.
import java.io.IOException;                           // Storage failure.
import java.math.BigDecimal;                          // Money.
import java.util.ArrayList;                           // Lists.
import java.util.List;                                // Lists.
import java.util.function.Consumer;                   // Callback.
import static com.kofi.pos.ui.common.UiSupport.*;    // Shared helpers.

/** Owns checkout controls and their temporary JavaFX state. */
public final class PointOfSaleView {
    private final PosService service;          // Business operations (cart lives inside it).
    private final String cashier;              // Signed-in user recorded on each sale.
    private final Consumer<String> notice;     // Top-bar message callback.
    private final Consumer<Sale> showReceipt;  // Opens the receipt dialog.
    // Controls that must be updated from several places (kept as fields):
    private TableView<CartLine> currentCartTable; // The cart table.
    private TextField cartSubtotalField;          // Read-only subtotal.
    private TextField cartTotalField;             // Read-only total.
    private TextField cartChangeField;            // Read-only change.
    private TextField cartDiscountField;          // Editable discount.
    private TextField cartTenderField;            // Editable cash tendered.
    private Label cartDiscountError;              // Discount message.
    private Label cartTenderError;                // Tender message.
    private Label cartLineError;                  // Quantity / stock message.
    private Button cartCheckoutButton;            // "Complete sale".
    private String invalidCartSku;                // SKU of a cart line that currently has a problem, else null.

    public PointOfSaleView(PosService service, String cashier, Consumer<String> notice, Consumer<Sale> showReceipt) {
        this.service = service;
        this.cashier = cashier;
        this.notice = notice;
        this.showReceipt = showReceipt;
    }

    /** Creates the screen's visual tree. */
    public Node build() {
        VBox root = page("Point of sale", "Tap a product to add it, review the cart, then take payment. Search by name or SKU to narrow the menu.");

        // ============ LEFT SIDE: search, category chips and the product tiles ============
        TextField search = new TextField();
        search.setPromptText("Search by name or SKU");
        search.setTextFormatter(limitFormatter(60));
        trimOnBlur(search);
        Button clear = new Button("×");
        clear.setTooltip(new javafx.scene.control.Tooltip("Clear search"));
        clear.getStyleClass().add("clear-button");
        HBox searchRow = new HBox(8, search, clear);
        HBox.setHgrow(search, Priority.ALWAYS);
        Label searchError = new Label();
        searchError.getStyleClass().add("field-error");

        ToggleGroup categories = new ToggleGroup(); // One chip is always selected: All, Coffee, Food ...
        HBox chips = new HBox(8);
        ArrayList<String> names = new ArrayList<>();
        names.add(null); // null stands for "All".
        for (Product product : service.searchProducts("")) if (!names.contains(product.category())) names.add(product.category());
        for (String category : names) {
            ToggleButton chip = new ToggleButton(category == null ? "All" : category);
            chip.setUserData(category);
            chip.setToggleGroup(categories);
            chip.getStyleClass().add("chip");
            chips.getChildren().add(chip);
        }
        categories.selectToggle(categories.getToggles().get(0));
        categories.selectedToggleProperty().addListener((obs, old, now) -> { if (now == null) old.setSelected(true); }); // Cannot untick.

        ScrollPane tileHost = new ScrollPane(); // The tiles scroll when there are many products.
        tileHost.setFitToWidth(true);
        tileHost.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tileHost.getStyleClass().add("content-scroll");
        tileHost.setPrefHeight(505);

        Consumer<Product> addProduct = product -> { // Called when a tile or size button is chosen.
            try {
                service.addToCart(product.sku());
                searchError.setText("");
                updateCartTable();
                updateCartTotals();
                notice.accept(product.displayName() + " added to cart");
            } catch (IllegalArgumentException e) {
                searchError.setText(e.getMessage());
            }
        };
        Runnable showTiles = () -> { // Linear search by text, then keep only the chosen category.
            String category = (String) categories.getSelectedToggle().getUserData();
            ArrayList<Product> shown = new ArrayList<>();
            for (Product product : service.searchProducts(search.getText()))
                if (category == null || product.category().equalsIgnoreCase(category)) shown.add(product);
            if (shown.isEmpty()) {
                Label none = new Label(search.getText().isBlank() ? "No products in this category." : "No products match \"" + search.getText() + "\". Clear the search to see everything.");
                none.getStyleClass().add("muted-text");
                none.setWrapText(true);
                none.setPadding(new Insets(12));
                tileHost.setContent(none);
            } else tileHost.setContent(ProductTiles.grid(shown, addProduct));
        };
        PauseTransition debounce = new PauseTransition(Duration.millis(250)); // Wait 250ms after the last keystroke.
        search.textProperty().addListener((obs, old, value) -> {
            debounce.stop();                                                   // Cancel the previous pending search.
            debounce.setOnFinished(event -> showTiles.run());
            debounce.playFromStart();                                          // Restart the countdown.
        });
        categories.selectedToggleProperty().addListener((obs, old, now) -> showTiles.run());
        clear.setOnAction(event -> { search.clear(); search.requestFocus(); });
        search.setOnAction(event -> { // Enter chooses the first tile, like clicking it.
            debounce.stop();
            showTiles.run();
            if (tileHost.getContent() instanceof FlowPane grid && !grid.getChildren().isEmpty()
                    && grid.getChildren().get(0) instanceof Button first && !first.isDisabled()) first.fire();
            event.consume();
        });
        showTiles.run();
        VBox catalog = new VBox(10, searchRow, chips, searchError, tileHost);
        VBox.setVgrow(tileHost, Priority.ALWAYS);

        // ============ RIGHT SIDE: the cart (a LinkedList inside PosService) ============
        Label cartTitle = new Label("Current sale");
        cartTitle.getStyleClass().add("section-heading");
        Label lineError = new Label();
        lineError.getStyleClass().add("field-error");
        cartLineError = lineError;
        TableView<CartLine> cartTable = new TableView<>();
        currentCartTable = cartTable;
        TableColumn<CartLine, String> item = new TableColumn<>("Item");
        item.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().product().displayName()));
        item.setCellFactory(column -> ProductTables.iconCell(CartLine::product)); // Icon beside each cart line.
        TableColumn<CartLine, Integer> quantity = new TableColumn<>("Qty");
        quantity.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().quantity()).asObject());
        quantity.setCellFactory(column -> new TableCell<>() { // Each Qty cell is an editable text box.
            private final TextField editor = new TextField();
            { // Instance initialiser: configure the editor once per cell.
                editor.setPrefColumnCount(4);
                editor.setTextFormatter(integerFormatter());
                editor.setOnAction(event -> commit());                                   // Enter commits.
                editor.focusedProperty().addListener((obs, was, focused) -> { if (!focused) commit(); }); // Leaving the box commits.
            }
            /** Sends the typed quantity to the service and shows any stock error. */
            private void commit() {
                CartLine line = getTableRow() == null ? null : getTableRow().getItem();
                if (line == null) return;
                if (editor.getText().isBlank()) {
                    invalidCartSku = line.product().sku();
                    lineError.setText("Quantity is required.");
                    setInvalid(editor, true);
                    updateCartTotals();
                    return;
                }
                try {
                    service.setCartQuantity(line.product().sku(), Integer.parseInt(editor.getText()));
                    if (line.product().sku().equalsIgnoreCase(invalidCartSku)) {
                        invalidCartSku = null;
                        lineError.setText("");
                    } else if (invalidCartSku == null) lineError.setText("");
                    setInvalid(editor, false);
                } catch (IllegalArgumentException e) {
                    invalidCartSku = line.product().sku();
                    lineError.setText(e.getMessage());
                    setInvalid(editor, true);
                }
                updateCartTable();
                updateCartTotals();
            }
            @Override protected void updateItem(Integer value, boolean empty) { // Called by JavaFX when the cell is (re)used.
                super.updateItem(value, empty);
                if (empty || value == null) setGraphic(null);
                else {
                    if (!editor.isFocused()) editor.setText(value.toString());
                    setGraphic(editor);
                }
            }
        });
        TableColumn<CartLine, String> lineTotal = new TableColumn<>("Amount");
        lineTotal.setCellValueFactory(c -> new ReadOnlyStringWrapper(peso(c.getValue().product().sellingPrice().multiply(BigDecimal.valueOf(c.getValue().quantity())))));
        TableColumn<CartLine, Void> remove = new TableColumn<>("");
        remove.setCellFactory(column -> new TableCell<>() { // Each row gets a Remove button.
            private final Button button = new Button("Remove");
            {
                button.getStyleClass().add("text-button-danger");
                button.setOnAction(event -> {
                    CartLine line = getTableRow().getItem();
                    if (line != null) {
                        service.removeFromCart(line.product().sku()); // Unlink the line from the LinkedList.
                        if (line.product().sku().equalsIgnoreCase(invalidCartSku)) invalidCartSku = null;
                    }
                    if (invalidCartSku == null) lineError.setText("");
                    if (service.cart().isEmpty()) resetPaymentFields();
                    updateCartTable();
                    updateCartTotals();
                });
            }
            @Override protected void updateItem(Void value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty ? null : button);
            }
        });
        cartTable.getColumns().addAll(List.of(item, quantity, lineTotal, remove));
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cartTable.setPlaceholder(new Label("Search for a product to begin."));
        cartTable.setPrefHeight(320);
        cartTable.setItems(FXCollections.observableArrayList(service.cart()));

        // ---- Totals and payment inputs ----
        TextField subtotal = readOnlyField();
        TextField total = readOnlyField();
        TextField change = readOnlyField();
        cartSubtotalField = subtotal;
        cartTotalField = total;
        cartChangeField = change;
        TextField discount = new TextField("0.00");
        discount.setTextFormatter(moneyFormatter());
        cartDiscountField = discount;
        TextField tender = new TextField();
        tender.setPromptText("0.00");
        tender.setTextFormatter(moneyFormatter());
        cartTenderField = tender;
        bindPesoBlur(discount);
        bindPesoBlur(tender);
        Label discountError = new Label();
        discountError.getStyleClass().add("field-error");
        cartDiscountError = discountError;
        Label tenderError = new Label();
        tenderError.getStyleClass().add("field-error");
        cartTenderError = tenderError;
        GridPane totals = new GridPane();
        totals.setHgap(12);
        totals.setVgap(9);
        totals.addRow(0, new Label("Subtotal"), subtotal);
        totals.addRow(1, new Label("Discount"), discount);
        totals.addRow(2, new Label("Total"), total);
        totals.addRow(3, new Label("Cash tendered *"), tender);
        totals.addRow(4, new Label("Change"), change);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(118);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        totals.getColumnConstraints().addAll(labelColumn, valueColumn);

        Button checkout = new Button("Complete sale");
        cartCheckoutButton = checkout;
        checkout.getStyleClass().add("primary-button");
        checkout.setMaxWidth(Double.MAX_VALUE);
        checkout.setDefaultButton(true);
        Button cancel = new Button("Clear cart");
        cancel.getStyleClass().add("quiet-button");
        cancel.setOnAction(event -> {
            service.clearCart();
            invalidCartSku = null;
            lineError.setText("");
            resetPaymentFields();
            updateCartTable();
            updateCartTotals();
        });
        checkout.setOnAction(event -> {
            try {
                BigDecimal discountAmount = discount.getText().isBlank() ? BigDecimal.ZERO : parsedMoney(discount.getText());
                Sale sale = service.checkout(cashier, discountAmount, parsedMoney(tender.getText())); // Validates, saves, returns the receipt.
                notice.accept("Sale saved · " + sale.receiptNo());
                invalidCartSku = null;
                lineError.setText("");
                resetPaymentFields();
                updateCartTable();
                updateCartTotals();
                showReceipt.accept(sale); // Show the receipt preview.
            } catch (IllegalArgumentException | IOException e) {
                tenderError.setText(e.getMessage());
            }
        });
        discount.textProperty().addListener((obs, old, value) -> updateCartTotals());
        tender.textProperty().addListener((obs, old, value) -> updateCartTotals());
        tender.setOnAction(event -> { if (!checkout.isDisabled()) checkout.fire(); event.consume(); }); // Enter in tender completes the sale.
        discountError.setMaxWidth(Double.MAX_VALUE);
        tenderError.setMaxWidth(Double.MAX_VALUE);
        VBox cartPanel = new VBox(12, cartTitle, lineError, cartTable, totals, discountError, tenderError, checkout, cancel);
        cartPanel.getStyleClass().add("surface-panel");
        cartPanel.setPadding(new Insets(20));
        cartPanel.setPrefWidth(570);
        cartPanel.setMinWidth(470);
        HBox body = new HBox(20, catalog, cartPanel);
        HBox.setHgrow(catalog, Priority.ALWAYS);
        root.getChildren().add(body);
        updateCartTotals(); // Initial calculation.
        return root;
    }

    /** Re-reads the cart from the service into the table. */
    private void updateCartTable() {
        if (currentCartTable != null) currentCartTable.setItems(FXCollections.observableArrayList(service.cart()));
    }

    /** Puts the discount and tender boxes back to their starting values. */
    private void resetPaymentFields() {
        if (cartDiscountField == null || cartTenderField == null) return;
        cartDiscountField.setText("0.00");
        cartTenderField.clear();
    }

    /** Recomputes subtotal/total/change, shows validation messages, and enables or disables checkout. */
    private void updateCartTotals() {
        if (cartSubtotalField == null) return; // Screen not built yet.
        if (invalidCartSku != null && cartLineError != null && cartLineError.getText().isBlank()) {
            for (CartLine line : service.cart()) { // Re-explain why a flagged line is invalid.
                if (line.product().sku().equalsIgnoreCase(invalidCartSku)) {
                    Product current = service.findProduct(invalidCartSku);
                    if (current == null || !current.active()) cartLineError.setText("Product is no longer active. Remove it from the cart.");
                    else if (line.quantity() > current.qtyOnHand()) cartLineError.setText("Only " + current.qtyOnHand() + " in stock");
                    else cartLineError.setText("Review quantity for " + invalidCartSku + " before checkout.");
                    break;
                }
            }
        }
        if (invalidCartSku == null) { // Look for any line that became invalid since the last check.
            for (CartLine line : service.cart()) {
                Product current = service.findProduct(line.product().sku());
                if (current == null || !current.active()) {
                    invalidCartSku = line.product().sku();
                    cartLineError.setText("Product is no longer active. Remove it from the cart.");
                    break;
                }
                if (line.quantity() > current.qtyOnHand()) {
                    invalidCartSku = line.product().sku();
                    cartLineError.setText("Only " + current.qtyOnHand() + " in stock");
                    break;
                }
            }
        }
        BigDecimal subtotal = service.cartSubtotal();
        BigDecimal discount = cartDiscountField.getText().isBlank() ? BigDecimal.ZERO : parsedMoney(cartDiscountField.getText());
        BigDecimal tender = parsedMoney(cartTenderField.getText());
        boolean discountValid = discount != null && discount.signum() >= 0 && discount.compareTo(subtotal) <= 0;
        BigDecimal total = service.total(discount);
        boolean hasCart = !service.cart().isEmpty();
        boolean tenderValid = tender != null && tender.compareTo(total) >= 0 && hasCart;
        cartSubtotalField.setText(peso(subtotal));
        cartTotalField.setText(peso(discountValid ? total : BigDecimal.ZERO));
        cartChangeField.setText(peso(discountValid && tenderValid ? service.change(discount, tender) : BigDecimal.ZERO));
        showFieldError(cartDiscountField, cartDiscountError, discountValid ? "" : "Discount cannot exceed subtotal.");
        showFieldError(cartTenderField, cartTenderError, tenderValid || !hasCart ? "" : "Cash tendered must be at least the total.");
        cartCheckoutButton.setDisable(!hasCart || !discountValid || !tenderValid || invalidCartSku != null); // All conditions must hold.
    }

    /** Escape key: clears the search box if focused there, otherwise abandons the whole cart. */
    public void handleEscape(Node focus) {
        if (focus instanceof TextField field && field.getPromptText().contains("name or SKU")) field.clear();
        else {
            service.clearCart();
            invalidCartSku = null;
            if (cartLineError != null) cartLineError.setText("");
            resetPaymentFields();
            updateCartTable();
            updateCartTotals();
        }
    }
}
