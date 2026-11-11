package com.cc104.pos.ui;

import com.cc104.pos.model.CartLine;
import com.cc104.pos.model.Product;
import com.cc104.pos.model.Sale;
import com.cc104.pos.service.PosService;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;
import static com.cc104.pos.ui.UiSupport.*;

/** Owns checkout controls and their temporary JavaFX state. */
public final class PointOfSaleView {
    private final PosService service;
    private final String cashier;
    private final Consumer<String> notice;
    private final Consumer<Sale> showReceipt;
    private TableView<CartLine> currentCartTable;
    private TextField cartSubtotalField;
    private TextField cartTotalField;
    private TextField cartChangeField;
    private TextField cartDiscountField;
    private TextField cartTenderField;
    private Label cartDiscountError;
    private Label cartTenderError;
    private Label cartLineError;
    private Button cartCheckoutButton;
    private String invalidCartSku;

    public PointOfSaleView(PosService service, String cashier, Consumer<String> notice, Consumer<Sale> showReceipt) {
        this.service = service;
        this.cashier = cashier;
        this.notice = notice;
        this.showReceipt = showReceipt;
    }
    public Node build() {
        VBox root = page("Point of sale", "Search by product name or SKU, then review the cart before taking payment.");
        TableView<Product> results = ProductTables.create(false);
        results.setItems(FXCollections.observableArrayList(service.searchProducts("")));
        results.setPrefHeight(505);
        TextField search = new TextField();
        search.setPromptText("Search by name or SKU");
        search.setTextFormatter(limitFormatter(60));
        trimOnBlur(search);
        Button clear = new Button("×");
        clear.setTooltip(new javafx.scene.control.Tooltip("Clear search"));
        clear.getStyleClass().add("clear-button");
        HBox searchRow = new HBox(8, search, clear);
        HBox.setHgrow(search, Priority.ALWAYS);
        PauseTransition debounce = new PauseTransition(Duration.millis(250));
        search.textProperty().addListener((obs, old, value) -> {
            debounce.stop();
            debounce.setOnFinished(event -> results.setItems(FXCollections.observableArrayList(service.searchProducts(value))));
            debounce.playFromStart();
        });
        clear.setOnAction(event -> { search.clear(); search.requestFocus(); });
        Button add = new Button("Add selected product");
        add.getStyleClass().add("secondary-button");
        Label searchError = new Label();
        searchError.getStyleClass().add("field-error");
        Runnable addProduct = () -> {
            List<Product> matches = service.searchProducts(search.getText());
            Product product = results.getSelectionModel().getSelectedItem();
            if (product == null && !matches.isEmpty()) product = matches.get(0);
            if (product == null) { searchError.setText("No matching active product."); return; }
            try {
                service.addToCart(product.sku());
                searchError.setText("");
                updateCartTable();
                updateCartTotals();
                notice.accept(product.name() + " added to cart");
            } catch (IllegalArgumentException e) {
                searchError.setText(e.getMessage());
            }
        };
        add.setOnAction(event -> addProduct.run());
        search.setOnAction(event -> { addProduct.run(); event.consume(); });
        results.setOnMouseClicked(event -> { if (event.getClickCount() == 2) addProduct.run(); });
        VBox catalog = new VBox(10, searchRow, searchError, results, add);
        VBox.setVgrow(results, Priority.ALWAYS);

        Label cartTitle = new Label("Current sale");
        cartTitle.getStyleClass().add("section-heading");
        Label lineError = new Label();
        lineError.getStyleClass().add("field-error");
        cartLineError = lineError;
        TableView<CartLine> cartTable = new TableView<>();
        currentCartTable = cartTable;
        TableColumn<CartLine, String> item = new TableColumn<>("Item");
        item.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().product().name()));
        TableColumn<CartLine, Integer> quantity = new TableColumn<>("Qty");
        quantity.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().quantity()).asObject());
        quantity.setCellFactory(column -> new TableCell<>() {
            private final TextField editor = new TextField();
            {
                editor.setPrefColumnCount(4);
                editor.setTextFormatter(integerFormatter());
                editor.setOnAction(event -> commit());
                editor.focusedProperty().addListener((obs, was, focused) -> { if (!focused) commit(); });
            }
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
            @Override protected void updateItem(Integer value, boolean empty) {
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
        remove.setCellFactory(column -> new TableCell<>() {
            private final Button button = new Button("Remove");
            {
                button.getStyleClass().add("text-button-danger");
                button.setOnAction(event -> {
                    CartLine line = getTableRow().getItem();
                    if (line != null) {
                        service.removeFromCart(line.product().sku());
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
                Sale sale = service.checkout(cashier, discountAmount, parsedMoney(tender.getText()));
                notice.accept("Sale saved · " + sale.receiptNo());
                invalidCartSku = null;
                lineError.setText("");
                resetPaymentFields();
                updateCartTable();
                updateCartTotals();
                showReceipt.accept(sale);
            } catch (IllegalArgumentException | IOException e) {
                tenderError.setText(e.getMessage());
            }
        });
        discount.textProperty().addListener((obs, old, value) -> updateCartTotals());
        tender.textProperty().addListener((obs, old, value) -> updateCartTotals());
        tender.setOnAction(event -> { if (!checkout.isDisabled()) checkout.fire(); event.consume(); });
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
        updateCartTotals();
        return root;
    }

    private void updateCartTable() {
        if (currentCartTable != null) currentCartTable.setItems(FXCollections.observableArrayList(service.cart()));
    }

    private void resetPaymentFields() {
        if (cartDiscountField == null || cartTenderField == null) return;
        cartDiscountField.setText("0.00");
        cartTenderField.clear();
    }

    private void updateCartTotals() {
        if (cartSubtotalField == null) return;
        if (invalidCartSku != null && cartLineError != null && cartLineError.getText().isBlank()) {
            for (CartLine line : service.cart()) {
                if (line.product().sku().equalsIgnoreCase(invalidCartSku)) {
                    Product current = service.findProduct(invalidCartSku);
                    if (current == null || !current.active()) cartLineError.setText("Product is no longer active. Remove it from the cart.");
                    else if (line.quantity() > current.qtyOnHand()) cartLineError.setText("Only " + current.qtyOnHand() + " in stock");
                    else cartLineError.setText("Review quantity for " + invalidCartSku + " before checkout.");
                    break;
                }
            }
        }
        if (invalidCartSku == null) {
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
        cartCheckoutButton.setDisable(!hasCart || !discountValid || !tenderValid || invalidCartSku != null);
    }

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
