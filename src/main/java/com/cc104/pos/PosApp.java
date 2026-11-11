package com.cc104.pos;

import com.cc104.pos.service.PosService;
import com.cc104.pos.service.PosService.Activity;
import com.cc104.pos.service.PosService.CartLine;
import com.cc104.pos.service.PosService.Product;
import com.cc104.pos.service.PosService.RestockAlert;
import com.cc104.pos.service.PosService.Sale;
import com.cc104.pos.service.PosService.SaleLine;
import com.cc104.pos.service.PosService.Settings;
import com.cc104.pos.service.PosService.StockMovement;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PosApp extends Application {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US);
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.US);
    private static final String[] MODULES = {"Overview", "Point of Sale", "Products", "Stock", "Sales & Receipts", "Reports", "Settings"};

    static {
        MONEY.setMinimumFractionDigits(2);
        MONEY.setMaximumFractionDigits(2);
    }

    private Stage stage;
    private PosService service;
    private String cashier;
    private BorderPane appShell;
    private StackPane contentHost;
    private Label pageTitle;
    private Label statusLabel;
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
    private String currentModule = "Overview";
    private String productEditSku;
    private String pendingStockSku;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        try {
            service = new PosService();
            showLogin();
        } catch (IOException e) {
            showFatal("Local CSV data could not be opened", e.getMessage());
        }
    }

    private Scene scene(Parent root) {
        Scene scene = new Scene(root, 1360, 860);
        var stylesheet = getClass().getResource("/style.css");
        if (stylesheet != null) scene.getStylesheets().add(stylesheet.toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && currentModule.equals("Point of Sale")) {
                if (contentHost != null && contentHost.getScene() != null) {
                    Node focus = contentHost.getScene().getFocusOwner();
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
        });
        return scene;
    }

    private void showLogin() {
        Label brand = new Label("Tindahan POS");
        brand.getStyleClass().add("brand-title");
        Label subBrand = new Label("OFFLINE SALES & STOCK");
        subBrand.getStyleClass().add("brand-subtitle");

        Label title = new Label("Welcome back");
        title.getStyleClass().add("page-heading");
        Label description = new Label("Sign in to open the register and manage store records.");
        description.getStyleClass().add("muted-text");

        TextField username = new TextField();
        username.setPromptText("Username");
        username.setTextFormatter(limitFormatter(30));
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setTextFormatter(limitFormatter(128));
        Label error = new Label();
        error.getStyleClass().add("field-error");
        Button login = new Button("Sign in");
        login.getStyleClass().add("primary-button");
        login.setMaxWidth(Double.MAX_VALUE);
        login.setDefaultButton(true);
        login.disableProperty().bind(username.textProperty().isEmpty().or(password.textProperty().isEmpty()));

        Runnable submit = () -> {
            String cleanUsername = username.getText().trim();
            username.setText(cleanUsername);
            if (cleanUsername.isEmpty() || cleanUsername.length() > 30) {
                showFieldError(username, error, "Enter a username up to 30 characters.");
                return;
            }
            if (!service.authenticate(cleanUsername, password.getText())) {
                password.clear();
                showFieldError(username, error, "Username or password is incorrect.");
                showFieldError(password, error, "Username or password is incorrect.");
                password.requestFocus();
                return;
            }
            cashier = cleanUsername;
            showShell("Overview");
        };
        login.setOnAction(event -> submit.run());
        username.setOnAction(event -> { submit.run(); event.consume(); });
        password.setOnAction(event -> { submit.run(); event.consume(); });
        username.textProperty().addListener((obs, old, value) -> {
            showFieldError(username, error, "");
            showFieldError(password, error, "");
        });
        password.textProperty().addListener((obs, old, value) -> {
            showFieldError(username, error, "");
            showFieldError(password, error, "");
        });
        username.focusedProperty().addListener((obs, old, focused) -> { if (!focused) username.setText(username.getText().trim()); });

        Label demo = new Label("Demo access  ·  admin / admin123");
        demo.getStyleClass().add("muted-small");
        VBox form = new VBox(14, brand, subBrand, new Separator(), title, description,
                field("Username *", username), field("Password *", password), error, login, demo);
        form.getStyleClass().add("login-panel");
        form.setMaxWidth(430);
        form.setPadding(new Insets(34));

        StackPane root = new StackPane(form);
        root.getStyleClass().add("login-background");
        root.setPadding(new Insets(32));
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                username.clear();
                password.clear();
                error.setText("");
            }
        });
        stage.setTitle("Tindahan POS · Sign in");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        stage.setScene(scene(root));
        stage.show();
        username.requestFocus();
    }

    private void showShell(String module) {
        appShell = new BorderPane();
        appShell.getStyleClass().add("app-root");
        contentHost = new StackPane();
        contentHost.getStyleClass().add("content-host");
        appShell.setLeft(buildNavigation());
        appShell.setTop(buildTopBar());
        appShell.setCenter(contentHost);
        stage.setTitle(service.settings().storeName() + " · POS");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene(appShell));
        stage.setMaximized(true);
        showModule(module);
    }

    private VBox buildNavigation() {
        Label brand = new Label(service.settings().storeName());
        brand.getStyleClass().add("nav-brand");
        Label kind = new Label("LOCAL REGISTER");
        kind.getStyleClass().add("nav-caption");
        VBox brandBox = new VBox(4, brand, kind);
        brandBox.setPadding(new Insets(24, 18, 24, 20));

        VBox links = new VBox(6);
        links.setPadding(new Insets(8, 10, 10, 10));
        for (String module : MODULES) {
            Button link = new Button(module);
            link.getStyleClass().add("nav-button");
            link.setMaxWidth(Double.MAX_VALUE);
            link.setAlignment(Pos.CENTER_LEFT);
            link.setOnAction(event -> showModule(module));
            links.getChildren().add(link);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label local = new Label("Works without internet\nRecords saved to local CSV");
        local.getStyleClass().add("nav-footnote");
        local.setPadding(new Insets(14, 18, 20, 18));
        Button quit = new Button("Exit application");
        quit.getStyleClass().add("nav-button");
        quit.setMaxWidth(Double.MAX_VALUE);
        quit.setAlignment(Pos.CENTER_LEFT);
        quit.setOnAction(event -> {
            if (!service.cart().isEmpty()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "The current cart has not been checked out. Exit and discard it?", ButtonType.CANCEL, ButtonType.OK);
                confirm.initOwner(stage);
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                service.clearCart();
            }
            stage.close();
        });
        VBox nav = new VBox(brandBox, new Separator(), links, spacer, quit, local);
        nav.getStyleClass().add("sidebar");
        nav.setPrefWidth(224);
        return nav;
    }

    private HBox buildTopBar() {
        pageTitle = new Label();
        pageTitle.getStyleClass().add("topbar-title");
        statusLabel = new Label("Local data ready");
        statusLabel.getStyleClass().add("status-text");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label user = new Label("Signed in as  " + cashier);
        user.getStyleClass().add("user-chip");
        Button exit = new Button("Sign out");
        exit.getStyleClass().add("quiet-button");
        exit.setOnAction(event -> { service.clearCart(); invalidCartSku = null; showLogin(); });
        HBox bar = new HBox(18, pageTitle, statusLabel, spacer, user, exit);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(16, 24, 16, 28));
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private void showModule(String module) {
        currentModule = module;
        pageTitle.setText(module);
        Node view = switch (module) {
            case "Overview" -> dashboardView();
            case "Point of Sale" -> posView();
            case "Products" -> productsView();
            case "Stock" -> stockView();
            case "Sales & Receipts" -> salesView();
            case "Reports" -> reportsView();
            case "Settings" -> settingsView();
            default -> dashboardView();
        };
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        contentHost.getChildren().setAll(scroll);
        for (Node node : ((VBox) appShell.getLeft()).getChildren()) {
            if (node instanceof VBox links) {
                for (Node link : links.getChildren()) {
                    if (link instanceof Button button) {
                        button.getStyleClass().remove("nav-button-active");
                        if (button.getText().equals(module)) button.getStyleClass().add("nav-button-active");
                    }
                }
            }
        }
    }

    private VBox page(String heading, String description) {
        Label title = new Label(heading);
        title.getStyleClass().add("page-heading");
        Label sub = new Label(description);
        sub.getStyleClass().add("page-description");
        VBox header = new VBox(6, title, sub);
        header.setPadding(new Insets(4, 0, 20, 0));
        VBox page = new VBox(18, header);
        page.setPadding(new Insets(26, 30, 36, 30));
        page.getStyleClass().add("page-content");
        return page;
    }

    private Node dashboardView() {
        VBox root = page("Store overview", "A quick view of today’s sales and the stock that needs attention.");
        LocalDate today = LocalDate.now();
        HBox metrics = new HBox(0,
                metric("Sales today", peso(service.salesTotal(today, today)), "Completed receipts only"),
                metric("Transactions", Integer.toString(service.salesCount(today, today)), "Today"),
                metric("Inventory at cost", peso(service.inventoryCostValue()), "Current on-hand stock"));
        metrics.getStyleClass().add("metric-strip");
        for (Node child : metrics.getChildren()) HBox.setHgrow(child, Priority.ALWAYS);

        TableView<RestockAlert> alerts = new TableView<>();
        TableColumn<RestockAlert, String> alertName = new TableColumn<>("Product");
        alertName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().productName()));
        TableColumn<RestockAlert, String> alertSku = new TableColumn<>("SKU");
        alertSku.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().sku()));
        TableColumn<RestockAlert, Number> alertQty = new TableColumn<>("On hand");
        alertQty.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().quantity()));
        TableColumn<RestockAlert, Number> reorder = new TableColumn<>("Reorder at");
        reorder.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().reorderLevel()));
        alerts.getColumns().addAll(List.of(alertName, alertSku, alertQty, reorder));
        alerts.setItems(FXCollections.observableArrayList(service.restockAlerts()));
        alerts.setPlaceholder(new Label("Stock levels look good. No restock alerts."));
        alerts.setPrefHeight(300);
        Button restock = new Button("Receive next restock");
        restock.getStyleClass().add("secondary-button");
        restock.setDisable(service.nextRestockAlert().isEmpty());
        restock.setOnAction(event -> {
            service.nextRestockAlert().ifPresent(alert -> pendingStockSku = alert.sku());
            showModule("Stock");
        });
        VBox alertPanel = panel("Restock queue · " + service.pendingRestockCount(), alerts, restock);

        TableView<Activity> activity = new TableView<>();
        TableColumn<Activity, String> when = new TableColumn<>("Time");
        when.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().occurredAt().format(DateTimeFormatter.ofPattern("h:mm a"))));
        TableColumn<Activity, String> what = new TableColumn<>("Activity");
        what.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().action()));
        TableColumn<Activity, String> detail = new TableColumn<>("Detail");
        detail.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().detail()));
        activity.getColumns().addAll(List.of(when, what, detail));
        activity.setItems(FXCollections.observableArrayList(service.recentActivities()));
        activity.setPlaceholder(new Label("Recent sales and stock actions will appear here."));
        activity.setPrefHeight(300);
        VBox activityPanel = panel("Recent activity", activity);
        HBox lower = new HBox(18, alertPanel, activityPanel);
        HBox.setHgrow(alertPanel, Priority.ALWAYS);
        HBox.setHgrow(activityPanel, Priority.ALWAYS);
        root.getChildren().addAll(metrics, lower);
        return root;
    }

    private VBox metric(String labelText, String valueText, String noteText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("metric-label");
        Label value = new Label(valueText);
        value.getStyleClass().add("metric-value");
        Label note = new Label(noteText);
        note.getStyleClass().add("muted-small");
        VBox box = new VBox(8, label, value, note);
        box.getStyleClass().add("metric-block");
        return box;
    }

    private Node posView() {
        VBox root = page("Point of sale", "Search by product name or SKU, then review the cart before taking payment.");
        TableView<Product> results = productTable(false);
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
                showNotice(product.name() + " added to cart");
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
                showNotice("Sale saved · " + sale.receiptNo());
                invalidCartSku = null;
                lineError.setText("");
                resetPaymentFields();
                updateCartTable();
                updateCartTotals();
                showReceiptDialog(sale);
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

    private Node productsView() {
        VBox root = page("Products", "Maintain product details, search records, and choose a hand-written sort.");
        TextField search = new TextField();
        search.setPromptText("Search name or SKU");
        search.setTextFormatter(limitFormatter(80));
        trimOnBlur(search);
        ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList("Name A–Z · insertion sort", "Stock low to high · selection sort"));
        sort.getSelectionModel().selectFirst();
        Button add = new Button("New product");
        add.getStyleClass().add("secondary-button");
        HBox tools = new HBox(10, search, sort, add);
        tools.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        TableView<Product> table = productTable(true);
        table.setPrefHeight(640);
        Runnable filter = () -> {
            List<Product> sorted = sort.getSelectionModel().getSelectedIndex() == 0 ? service.sortProductsByName() : service.sortProductsByQuantity();
            String needle = search.getText().trim().toLowerCase(Locale.ROOT);
            ArrayList<Product> matches = new ArrayList<>();
            for (Product product : sorted) if (product.name().toLowerCase(Locale.ROOT).contains(needle)
                    || product.sku().toLowerCase(Locale.ROOT).contains(needle)) matches.add(product);
            table.setItems(FXCollections.observableArrayList(matches));
        };
        search.textProperty().addListener((obs, old, value) -> filter.run());
        sort.valueProperty().addListener((obs, old, value) -> filter.run());

        VBox form = productForm(table, filter);
        HBox body = new HBox(18, new VBox(12, tools, table), form);
        HBox.setHgrow(body.getChildren().get(0), Priority.ALWAYS);
        form.setPrefWidth(360);
        add.setOnAction(event -> { table.getSelectionModel().clearSelection(); clearProductForm(form); productEditSku = null; });
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, product) -> {
            if (product != null) fillProductForm(form, product);
        });
        filter.run();
        root.getChildren().add(body);
        return root;
    }

    private TableView<Product> productTable(boolean showActive) {
        TableView<Product> table = new TableView<>();
        TableColumn<Product, String> sku = new TableColumn<>("SKU");
        sku.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().sku()));
        TableColumn<Product, String> name = new TableColumn<>("Product");
        name.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));
        TableColumn<Product, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().category()));
        TableColumn<Product, String> price = new TableColumn<>("Price");
        price.setCellValueFactory(c -> new ReadOnlyStringWrapper(peso(c.getValue().sellingPrice())));
        TableColumn<Product, Number> quantity = new TableColumn<>("On hand");
        quantity.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().qtyOnHand()));
        table.getColumns().addAll(List.of(sku, name, category, price, quantity));
        if (showActive) {
            TableColumn<Product, String> status = new TableColumn<>("Status");
            status.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().active() ? "Active" : "Inactive"));
            table.getColumns().add(status);
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No products match this search."));
        table.setSortPolicy(ignored -> false);
        return table;
    }

    private VBox productForm(TableView<Product> table, Runnable filter) {
        Label title = new Label("Product details");
        title.getStyleClass().add("section-heading");
        TextField sku = new TextField();
        sku.setTextFormatter(limitFormatter(30));
        TextField name = new TextField();
        name.setTextFormatter(limitFormatter(100));
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList("Beverages", "Coffee & Tea", "Household", "Instant Meals", "Rice & Grains"));
        category.setEditable(true);
        category.setPromptText("Optional");
        ComboBox<String> unit = new ComboBox<>(FXCollections.observableArrayList(service.units()));
        unit.getSelectionModel().select("pc");
        category.getEditor().focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) category.getEditor().setText(category.getEditor().getText().trim());
        });
        TextField cost = new TextField("0.00");
        cost.setTextFormatter(moneyFormatter());
        TextField price = new TextField("0.00");
        price.setTextFormatter(moneyFormatter());
        bindPesoBlur(cost);
        bindPesoBlur(price);
        TextField reorder = new TextField("5");
        reorder.setTextFormatter(integerFormatter());
        TextField qty = readOnlyField();
        qty.setText("0");
        Label skuError = errorLabel();
        Label nameError = errorLabel();
        Label costError = errorLabel();
        Label priceError = errorLabel();
        Label reorderError = errorLabel();
        Label warning = new Label();
        warning.getStyleClass().add("warning-text");
        Label message = errorLabel();
        Button save = new Button("Save product");
        save.getStyleClass().add("primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        Button clear = new Button("Clear form");
        clear.getStyleClass().add("quiet-button");
        Button delete = new Button("Delete selected");
        delete.getStyleClass().add("text-button-danger");
        delete.setMaxWidth(Double.MAX_VALUE);
        delete.setDisable(true);

        VBox box = new VBox(10, title, field("SKU *", sku), skuError, field("Product name *", name), nameError,
                field("Category", category), field("Unit *", unit), field("Cost price *", cost), costError,
                field("Selling price *", price), priceError, warning, field("Reorder level *", reorder), reorderError,
                field("Qty on hand", qty), message, save, clear, delete);
        box.getStyleClass().add("surface-panel");
        box.setPadding(new Insets(18));
        productEditSku = null;

        Runnable validate = () -> {
            String cleanSku = sku.getText().trim().toUpperCase(Locale.ROOT);
            String cleanName = name.getText().trim();
            BigDecimal c = parsedMoney(cost.getText());
            BigDecimal p = parsedMoney(price.getText());
            boolean duplicate = !cleanSku.isEmpty() && service.skuExists(cleanSku, productEditSku);
            showFieldError(sku, skuError, duplicate ? "SKU already exists." : cleanSku.isEmpty() ? "SKU is required." : "");
            showFieldError(name, nameError, cleanName.isEmpty() ? "Product name is required." : "");
            showFieldError(cost, costError, c == null ? "Enter a valid price with up to 2 decimals." : "");
            showFieldError(price, priceError, p == null ? "Enter a valid price with up to 2 decimals." : "");
            showFieldError(reorder, reorderError, reorder.getText().isBlank() ? "Reorder level is required." : "");
            warning.setText(c != null && p != null && p.compareTo(c) < 0 ? "Selling price is below cost." : "");
            save.setDisable(cleanSku.isEmpty() || duplicate || cleanName.isEmpty() || c == null || p == null
                    || reorder.getText().isBlank() || unit.getValue() == null);
        };
        sku.textProperty().addListener((obs, old, value) -> validate.run());
        sku.textProperty().addListener((obs, old, value) -> {
            String uppercase = value.toUpperCase(Locale.ROOT);
            if (!value.equals(uppercase)) {
                int caret = sku.getCaretPosition();
                sku.setText(uppercase);
                sku.positionCaret(Math.min(caret, uppercase.length()));
            }
        });
        name.textProperty().addListener((obs, old, value) -> validate.run());
        cost.textProperty().addListener((obs, old, value) -> validate.run());
        price.textProperty().addListener((obs, old, value) -> validate.run());
        reorder.textProperty().addListener((obs, old, value) -> validate.run());
        unit.valueProperty().addListener((obs, old, value) -> validate.run());
        sku.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) {
                String upper = sku.getText().trim().toUpperCase(Locale.ROOT);
                sku.setText(upper);
                validate.run();
            }
        });
        name.focusedProperty().addListener((obs, was, focused) -> { if (!focused) name.setText(name.getText().trim()); });
        save.setOnAction(event -> {
            try {
                service.saveProduct(productEditSku, sku.getText(), name.getText(), category.getEditor().getText(), unit.getValue(),
                        parsedMoney(cost.getText()), parsedMoney(price.getText()), Integer.parseInt(reorder.getText()));
                message.setText("");
                showNotice(productEditSku == null ? "Product added" : "Product updated");
                clearProductForm(box);
                filter.run();
            } catch (IllegalArgumentException | IOException e) {
                message.setText(e.getMessage());
            }
        });
        save.setDefaultButton(true);
        clear.setOnAction(event -> { clearProductForm(box); table.getSelectionModel().clearSelection(); });
        delete.setOnAction(event -> {
            Product selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { message.setText("Select a product first."); return; }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + selected.name() + "? Products with stock or history will be deactivated.", ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(stage);
            confirm.setTitle("Delete product");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                try {
                    service.deleteProduct(selected.sku());
                    clearProductForm(box);
                    filter.run();
                    showNotice("Product deleted or deactivated");
                } catch (IOException e) { message.setText(e.getMessage()); }
            }
        });
        validate.run();
        box.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) clearProductForm(box); });
        return box;
    }

    private void fillProductForm(VBox form, Product product) {
        productEditSku = product.sku();
        TextField sku = fieldControl(form, "SKU *", TextField.class);
        TextField name = fieldControl(form, "Product name *", TextField.class);
        ComboBox<?> category = fieldControl(form, "Category", ComboBox.class);
        @SuppressWarnings("unchecked") ComboBox<String> unit = (ComboBox<String>) fieldControl(form, "Unit *", ComboBox.class);
        TextField cost = fieldControl(form, "Cost price *", TextField.class);
        TextField price = fieldControl(form, "Selling price *", TextField.class);
        TextField reorder = fieldControl(form, "Reorder level *", TextField.class);
        TextField qty = fieldControl(form, "Qty on hand", TextField.class);
        if (sku != null) sku.setText(product.sku());
        if (name != null) name.setText(product.name());
        if (category != null) {
            @SuppressWarnings("unchecked") ComboBox<String> choices = (ComboBox<String>) category;
            choices.getEditor().setText(product.category());
            if (!choices.getItems().contains(product.category()) && !product.category().isBlank()) choices.getItems().add(product.category());
            choices.setValue(product.category());
        }
        if (unit != null) unit.setValue(product.unit());
        if (cost != null) cost.setText(product.costPrice().toPlainString());
        if (price != null) price.setText(product.sellingPrice().toPlainString());
        if (reorder != null) reorder.setText(Integer.toString(product.reorderLevel()));
        if (qty != null) qty.setText(Integer.toString(product.qtyOnHand()));
        Button delete = findButton(form, "Delete selected");
        if (delete != null) delete.setDisable(false);
        Button save = findButton(form, "Save product");
        if (save != null) save.setText("Update product");
    }

    private void clearProductForm(VBox form) {
        productEditSku = null;
        TextField sku = fieldControl(form, "SKU *", TextField.class);
        TextField name = fieldControl(form, "Product name *", TextField.class);
        ComboBox<?> category = fieldControl(form, "Category", ComboBox.class);
        @SuppressWarnings("unchecked") ComboBox<String> unit = (ComboBox<String>) fieldControl(form, "Unit *", ComboBox.class);
        TextField cost = fieldControl(form, "Cost price *", TextField.class);
        TextField price = fieldControl(form, "Selling price *", TextField.class);
        TextField reorder = fieldControl(form, "Reorder level *", TextField.class);
        TextField qty = fieldControl(form, "Qty on hand", TextField.class);
        if (sku != null) sku.clear();
        if (name != null) name.clear();
        if (category != null) { category.setValue(null); category.getEditor().clear(); }
        if (unit != null) unit.setValue("pc");
        if (cost != null) cost.setText("0.00");
        if (price != null) price.setText("0.00");
        if (reorder != null) reorder.setText("5");
        if (qty != null) qty.setText("0");
        Button delete = findButton(form, "Delete selected");
        if (delete != null) delete.setDisable(true);
        Button save = findButton(form, "Update product");
        if (save != null) save.setText("Save product");
        for (Node node : form.getChildren()) if (node instanceof Label label && label.getStyleClass().contains("field-error")) label.setText("");
    }

    private Node stockView() {
        VBox root = page("Stock movements", "Record stock in, stock out, and downward adjustments. Every change is saved to the local movement log.");
        VBox form = stockForm();
        TableView<StockMovement> history = new TableView<>();
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
        for (int i = 0, j = recent.size() - 1; i < j; i++, j--) {
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

    private VBox stockForm() {
        Label title = new Label("New movement");
        title.getStyleClass().add("section-heading");
        TextField productSearch = new TextField();
        productSearch.setPromptText("Search active products");
        productSearch.setTextFormatter(limitFormatter(60));
        trimOnBlur(productSearch);
        ComboBox<Product> product = new ComboBox<>(FXCollections.observableArrayList(service.activeProducts()));
        product.setPromptText("Select a product");
        product.setConverter(new StringConverter<>() {
            @Override public String toString(Product value) { return value == null ? "" : value.sku() + " · " + value.name(); }
            @Override public Product fromString(String value) {
                if (value == null) return null;
                for (Product candidate : service.activeProducts()) if (candidate.sku().equalsIgnoreCase(value.trim())
                        || candidate.name().equalsIgnoreCase(value.trim())) return candidate;
                return null;
            }
        });
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("Stock In", "Stock Out", "Adjust"));
        type.getSelectionModel().selectFirst();
        TextField quantity = new TextField();
        quantity.setTextFormatter(integerFormatter());
        TextArea reason = new TextArea();
        reason.setWrapText(true);
        reason.setPrefRowCount(4);
        reason.setTextFormatter(limitAreaFormatter(200));
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
        if (pendingStockSku != null) {
            Product next = service.findProduct(pendingStockSku);
            if (next != null) product.setValue(next);
            pendingStockSku = null;
        }
        productSearch.textProperty().addListener((obs, old, value) -> {
            ArrayList<Product> matches = new ArrayList<>();
            for (Product candidate : service.searchProducts(value)) matches.add(candidate);
            product.setItems(FXCollections.observableArrayList(matches));
            product.setValue(null);
            if (!value.isBlank()) product.show();
        });
        Runnable validateQuantity = () -> {
            Product selected = product.getValue();
            String entered = quantity.getText();
            String message = "";
            boolean quantityInvalid = entered.isBlank();
            boolean noteRequired = type.getValue() != null && !type.getValue().equals("Stock In");
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
            save.setDisable(!error.getText().isEmpty());
        };
        type.valueProperty().addListener((obs, old, value) -> {
            boolean required = value != null && !value.equals("Stock In");
            setRequiredLabel(box, "Reason / note", required);
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
                showNotice("Stock movement saved");
                showModule("Stock");
            } catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
        });
        box.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
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

    private Node salesView() {
        VBox root = page("Sales & receipts", "Find a receipt, review its line items, or void a completed sale with a reason.");
        TextField search = new TextField();
        search.setPromptText("Search receipt number or cashier");
        search.setTextFormatter(limitFormatter(50));
        trimOnBlur(search);
        ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList("Newest first · insertion sort", "Highest total · selection sort"));
        sort.getSelectionModel().selectFirst();
        HBox toolbar = new HBox(10, search, sort);
        HBox.setHgrow(search, Priority.ALWAYS);
        TableView<Sale> table = new TableView<>();
        TableColumn<Sale, String> receipt = new TableColumn<>("Receipt no.");
        receipt.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().receiptNo()));
        TableColumn<Sale, String> soldAt = new TableColumn<>("Date / time");
        soldAt.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().soldAt().format(DATE_TIME)));
        TableColumn<Sale, String> cashierColumn = new TableColumn<>("Cashier");
        cashierColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().cashier()));
        TableColumn<Sale, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(c -> new ReadOnlyStringWrapper(peso(c.getValue().total())));
        TableColumn<Sale, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().status()));
        table.getColumns().addAll(List.of(receipt, soldAt, cashierColumn, total, status));
        table.setPrefHeight(540);
        table.setSortPolicy(ignored -> false);
        Label empty = new Label("Select a receipt to review its details.");
        empty.getStyleClass().add("muted-text");
        VBox detail = new VBox(10, empty);
        detail.getStyleClass().add("surface-panel");
        detail.setPadding(new Insets(20));
        detail.setPrefWidth(440);
        Runnable filter = () -> {
            List<Sale> base = sort.getSelectionModel().getSelectedIndex() == 0 ? service.sortSalesByDate() : service.sortSalesByTotal();
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            ArrayList<Sale> matches = new ArrayList<>();
            for (Sale sale : base) if (query.isEmpty() || sale.receiptNo().toLowerCase(Locale.ROOT).contains(query)
                    || sale.cashier().toLowerCase(Locale.ROOT).contains(query)) matches.add(sale);
            table.setItems(FXCollections.observableArrayList(matches));
        };
        search.textProperty().addListener((obs, old, value) -> filter.run());
        sort.valueProperty().addListener((obs, old, value) -> filter.run());
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sale) -> {
            if (sale != null) showSaleDetail(detail, sale, filter, table);
        });
        filter.run();
        VBox list = new VBox(12, toolbar, table);
        HBox body = new HBox(18, list, detail);
        HBox.setHgrow(list, Priority.ALWAYS);
        root.getChildren().add(body);
        return root;
    }

    private void showSaleDetail(VBox detail, Sale sale, Runnable refresh, TableView<Sale> table) {
        detail.getChildren().clear();
        Label title = new Label("Receipt detail");
        title.getStyleClass().add("section-heading");
        Label number = new Label(sale.receiptNo());
        number.getStyleClass().add("receipt-number");
        Label date = new Label(sale.soldAt().format(DATE_TIME));
        Label state = new Label(sale.status());
        state.getStyleClass().add(sale.status().equals("VOID") ? "status-void" : "status-good");
        TableView<SaleLine> lines = new TableView<>();
        TableColumn<SaleLine, String> name = new TableColumn<>("Item");
        name.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().productName()));
        TableColumn<SaleLine, Number> qty = new TableColumn<>("Qty");
        qty.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().quantity()));
        TableColumn<SaleLine, String> amount = new TableColumn<>("Line total");
        amount.setCellValueFactory(c -> new ReadOnlyStringWrapper(peso(c.getValue().lineTotal())));
        lines.getColumns().addAll(List.of(name, qty, amount));
        lines.setItems(FXCollections.observableArrayList(service.saleLines(sale.receiptNo())));
        lines.setPrefHeight(210);
        GridPane totals = new GridPane();
        totals.setHgap(10);
        totals.setVgap(8);
        totals.addRow(0, new Label("Subtotal"), new Label(peso(sale.subtotal())));
        totals.addRow(1, new Label("Discount"), new Label(peso(sale.discount())));
        totals.addRow(2, new Label("Total"), new Label(peso(sale.total())));
        totals.addRow(3, new Label("Cash tendered"), new Label(peso(sale.cashTendered())));
        totals.addRow(4, new Label("Change"), new Label(peso(sale.changeAmount())));
        Label voidReason = new Label(sale.voidReason().isBlank() ? "" : "Void reason: " + sale.voidReason());
        voidReason.setWrapText(true);
        TextArea reason = new TextArea();
        reason.setPromptText("Reason for voiding");
        reason.setPrefRowCount(3);
        reason.setTextFormatter(limitAreaFormatter(150));
        Label count = counter(reason, 150);
        Label error = errorLabel();
        Button voidButton = new Button("Void sale");
        voidButton.getStyleClass().add("danger-button");
        voidButton.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> sale.status().equals("VOID") || reason.getText().trim().isEmpty(), reason.textProperty()));
        Runnable validateVoidReason = () -> showFieldError(reason, error,
                reason.getText().trim().isEmpty() ? "A reason is required to enable voiding." : "");
        reason.textProperty().addListener((obs, old, value) -> validateVoidReason.run());
        reason.focusedProperty().addListener((obs, was, focused) -> { if (!focused) reason.setText(reason.getText().trim()); });
        reason.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) { reason.clear(); event.consume(); } });
        validateVoidReason.run();
        voidButton.setOnAction(event -> {
            try {
                service.voidSale(sale.receiptNo(), reason.getText());
                refresh.run();
                showSaleDetail(detail, service.sale(sale.receiptNo()), refresh, table);
                showNotice("Receipt voided and stock restored");
            } catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
        });
        Button receiptView = new Button("Receipt preview");
        receiptView.getStyleClass().add("secondary-button");
        receiptView.setOnAction(event -> showReceiptDialog(sale));
        detail.getChildren().addAll(title, number, date, state, lines, totals, voidReason);
        if (!sale.status().equals("VOID")) detail.getChildren().addAll(field("Void reason *", reason), count, error, voidButton);
        detail.getChildren().add(receiptView);
    }

    private void showReceiptDialog(Sale sale) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Receipt · " + sale.receiptNo());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE));
        VBox receipt = new VBox(9);
        receipt.getStyleClass().add("receipt-paper");
        receipt.setPadding(new Insets(24));
        Label store = new Label(service.settings().storeName());
        store.getStyleClass().add("receipt-store");
        Label address = new Label(service.settings().storeAddress());
        Label number = new Label(sale.receiptNo());
        number.getStyleClass().add("receipt-number");
        receipt.getChildren().addAll(store, address, number, new Label(sale.soldAt().format(DATE_TIME)), new Label("Cashier: " + sale.cashier()), new Separator());
        for (SaleLine line : service.saleLines(sale.receiptNo())) {
            receipt.getChildren().add(new Label(line.productName() + "  ×  " + line.quantity() + "  ·  " + peso(line.lineTotal())));
        }
        receipt.getChildren().addAll(new Separator(), new Label("Subtotal  " + peso(sale.subtotal())),
                new Label("Discount  " + peso(sale.discount())), new Label("TOTAL  " + peso(sale.total())),
                new Label("Cash  " + peso(sale.cashTendered())), new Label("Change  " + peso(sale.changeAmount())));
        if (sale.status().equals("VOID")) receipt.getChildren().add(new Label("VOID · " + sale.voidReason()));
        if (!service.settings().receiptFooter().isBlank()) receipt.getChildren().add(new Label(service.settings().receiptFooter()));
        ScrollPane scroll = new ScrollPane(receipt);
        scroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.showAndWait();
    }

    private Node reportsView() {
        VBox root = page("Reports", "Review completed sales and stock valuation for a date range.");
        String today = LocalDate.now().toString();
        TextField from = new TextField(today);
        from.setPromptText("YYYY-MM-DD");
        from.setTextFormatter(dateFormatter());
        trimOnBlur(from);
        TextField to = new TextField(today);
        to.setPromptText("YYYY-MM-DD");
        to.setTextFormatter(dateFormatter());
        trimOnBlur(to);
        Label error = errorLabel();
        TextField salesAmount = readOnlyField();
        TextField count = readOnlyField();
        TextField stockValue = readOnlyField();
        Button apply = new Button("Run report");
        apply.getStyleClass().add("primary-button");
        HBox filters = new HBox(12, field("Date from *", from), field("Date to *", to), apply);
        filters.setAlignment(Pos.BOTTOM_LEFT);
        Label salesTitle = new Label("Sales in range");
        salesTitle.getStyleClass().add("section-heading");
        GridPane totals = new GridPane();
        totals.setHgap(14);
        totals.setVgap(10);
        totals.addRow(0, new Label("Net completed sales"), salesAmount);
        totals.addRow(1, new Label("Completed receipts"), count);
        totals.addRow(2, new Label("Current inventory at cost"), stockValue);

        TableView<Sale> table = new TableView<>();
        TableColumn<Sale, String> receipt = new TableColumn<>("Receipt no.");
        receipt.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().receiptNo()));
        TableColumn<Sale, String> date = new TableColumn<>("Sale date / time");
        date.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().soldAt().format(DATE_TIME)));
        TableColumn<Sale, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(c -> new ReadOnlyStringWrapper(peso(c.getValue().total())));
        table.getColumns().addAll(List.of(receipt, date, total));
        table.setPrefHeight(440);
        table.setPlaceholder(new Label("No completed receipts in this date range."));
        Runnable run = () -> {
            try {
                LocalDate first = LocalDate.parse(from.getText());
                LocalDate last = LocalDate.parse(to.getText());
                if (first.isAfter(LocalDate.now()) || last.isAfter(LocalDate.now())) throw new IllegalArgumentException("Future dates are not allowed.");
                if (first.isAfter(last)) throw new IllegalArgumentException("Date from must be on or before date to.");
                ArrayList<Sale> filtered = new ArrayList<>();
                for (Sale sale : service.sortSalesByDate()) if (sale.status().equals("COMPLETED")
                        && !sale.soldAt().toLocalDate().isBefore(first) && !sale.soldAt().toLocalDate().isAfter(last)) filtered.add(sale);
                table.setItems(FXCollections.observableArrayList(filtered));
                salesAmount.setText(peso(service.salesTotal(first, last)));
                count.setText(Integer.toString(service.salesCount(first, last)));
                stockValue.setText(peso(service.inventoryCostValue()));
                error.setText("");
                setInvalid(from, false);
                setInvalid(to, false);
                apply.setDisable(false);
            } catch (DateTimeParseException e) { error.setText("Use YYYY-MM-DD for both dates."); }
            catch (IllegalArgumentException e) { error.setText(e.getMessage()); }
        };
        apply.setOnAction(event -> run.run());
        apply.setDefaultButton(true);
        Runnable validateDates = () -> {
            try {
                LocalDate first = LocalDate.parse(from.getText());
                LocalDate last = LocalDate.parse(to.getText());
                if (first.isAfter(LocalDate.now()) || last.isAfter(LocalDate.now())) throw new IllegalArgumentException("Future dates are not allowed.");
                if (first.isAfter(last)) throw new IllegalArgumentException("Date from must be on or before date to.");
                error.setText("");
                setInvalid(from, false);
                setInvalid(to, false);
                apply.setDisable(false);
            } catch (DateTimeParseException e) {
                showFieldError(from, error, "Use YYYY-MM-DD for both dates.");
                setInvalid(to, true);
                apply.setDisable(true);
            } catch (IllegalArgumentException e) {
                showFieldError(from, error, e.getMessage());
                setInvalid(to, true);
                apply.setDisable(true);
            }
        };
        from.textProperty().addListener((obs, old, value) -> validateDates.run());
        to.textProperty().addListener((obs, old, value) -> validateDates.run());
        from.focusedProperty().addListener((obs, was, focused) -> { if (!focused) from.setText(from.getText().trim()); });
        to.focusedProperty().addListener((obs, was, focused) -> { if (!focused) to.setText(to.getText().trim()); });
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                from.setText(today);
                to.setText(today);
                run.run();
            }
        });
        VBox summary = panel("Report summary", salesTitle, totals, error);
        root.getChildren().addAll(filters, summary, table);
        validateDates.run();
        run.run();
        return root;
    }

    private Node settingsView() {
        VBox root = page("Store settings", "These details appear on the receipt preview.");
        Settings existing = service.settings();
        TextField name = new TextField(existing.storeName());
        name.setTextFormatter(limitFormatter(80));
        TextField address = new TextField(existing.storeAddress());
        address.setTextFormatter(limitFormatter(150));
        TextArea footer = new TextArea(existing.receiptFooter());
        footer.setWrapText(true);
        footer.setPrefRowCount(5);
        footer.setTextFormatter(limitAreaFormatter(200));
        Label count = counter(footer, 200);
        Label error = errorLabel();
        Button save = new Button("Save settings");
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);
        VBox form = new VBox(12, field("Store name *", name), field("Store address", address),
                field("Receipt footer", footer), count, error, save);
        form.getStyleClass().add("surface-panel");
        form.setPadding(new Insets(22));
        form.setMaxWidth(680);
        Runnable validate = () -> {
            boolean valid = !name.getText().trim().isEmpty();
            showFieldError(name, error, valid ? "" : "Store name is required.");
            save.setDisable(!valid);
        };
        name.textProperty().addListener((obs, old, value) -> validate.run());
        name.focusedProperty().addListener((obs, was, focused) -> { if (!focused) name.setText(name.getText().trim()); });
        address.focusedProperty().addListener((obs, was, focused) -> { if (!focused) address.setText(address.getText().trim()); });
        footer.focusedProperty().addListener((obs, was, focused) -> { if (!focused) footer.setText(footer.getText().trim()); });
        save.setOnAction(event -> {
            try {
                service.updateSettings(new Settings(name.getText(), address.getText(), footer.getText()));
                showNotice("Store settings saved");
                stage.setTitle(service.settings().storeName() + " · POS");
                appShell.setLeft(buildNavigation());
                showModule("Settings");
            } catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
        });
        form.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) showModule("Settings"); });
        validate.run();
        root.getChildren().add(form);
        return root;
    }

    private VBox panel(String title, Node... contents) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        VBox panel = new VBox(14, heading);
        panel.getChildren().addAll(contents);
        panel.getStyleClass().add("surface-panel");
        panel.setPadding(new Insets(18));
        return panel;
    }

    private VBox field(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(5, label, control);
        box.setFillWidth(true);
        if (control instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Label errorLabel() {
        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        return error;
    }

    private Label counter(TextArea area, int max) {
        Label count = new Label(area.getText().length() + " / " + max);
        count.getStyleClass().add("counter-label");
        area.textProperty().addListener((obs, old, value) -> count.setText(value.length() + " / " + max));
        return count;
    }

    private TextField readOnlyField() {
        TextField field = new TextField();
        field.setEditable(false);
        field.getStyleClass().add("readonly-field");
        return field;
    }

    private TextFormatter<String> limitFormatter(int max) {
        return new TextFormatter<>(change -> change.getControlNewText().length() <= max ? change : null);
    }

    private TextFormatter<String> limitAreaFormatter(int max) {
        return new TextFormatter<>(change -> change.getControlNewText().length() <= max ? change : null);
    }

    private TextFormatter<String> integerFormatter() {
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            return text.isEmpty() || text.matches("\\d{0,9}") ? change : null;
        });
    }

    private TextFormatter<String> moneyFormatter() {
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            return text.isEmpty() || text.matches("\\d{0,9}(\\.\\d{0,2})?") ? change : null;
        });
    }

    private TextFormatter<String> dateFormatter() {
        return new TextFormatter<>(change -> {
            String raw = change.getControlNewText().replace("-", "");
            if (!raw.matches("\\d{0,8}")) return null;
            StringBuilder formatted = new StringBuilder(raw);
            if (raw.length() > 4) formatted.insert(4, '-');
            if (raw.length() > 6) formatted.insert(7, '-');
            change.setRange(0, change.getControlText().length());
            change.setText(formatted.toString());
            change.setCaretPosition(formatted.length());
            change.setAnchor(formatted.length());
            return change;
        });
    }

    private void bindPesoBlur(TextField field) {
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                String raw = field.getText().replace("₱", "").replace(",", "").trim();
                replaceText(field, raw);
            } else {
                BigDecimal value = parsedMoney(field.getText());
                if (value != null && !field.getText().isBlank()) replaceText(field, peso(value));
            }
        });
    }

    private void trimOnBlur(TextField field) {
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) field.setText(field.getText().trim());
        });
    }

    private static void replaceText(TextField field, String value) {
        TextFormatter<?> existing = field.getTextFormatter();
        field.setTextFormatter(null);
        field.setText(value);
        field.setTextFormatter(existing);
    }

    private static BigDecimal parsedMoney(String text) {
        if (text == null || text.isBlank()) return null;
        try { return new BigDecimal(text.replace("₱", "").replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String peso(BigDecimal value) {
        return "₱" + MONEY.format(PosService.money(value));
    }

    private void showFieldError(TextField field, Label error, String message) {
        error.setText(message == null ? "" : message);
        setInvalid(field, message != null && !message.isBlank());
    }

    private void showFieldError(TextArea field, Label error, String message) {
        error.setText(message == null ? "" : message);
        setInvalid(field, message != null && !message.isBlank());
    }

    private void setInvalid(Node field, boolean invalid) {
        field.getStyleClass().remove("invalid-field");
        if (invalid) field.getStyleClass().add("invalid-field");
    }

    private void setRequiredLabel(VBox form, String fieldName, boolean required) {
        for (Node node : form.getChildren()) {
            if (node instanceof VBox field && !field.getChildren().isEmpty() && field.getChildren().get(0) instanceof Label label
                    && label.getText().startsWith(fieldName)) {
                label.setText(required ? fieldName + " *" : fieldName);
            }
        }
    }

    private <T extends Node> T fieldControl(VBox form, String label, Class<T> type) {
        for (Node node : form.getChildren()) if (node instanceof VBox field && field.getChildren().size() > 1
                && field.getChildren().get(0) instanceof Label text && text.getText().startsWith(label)
                && type.isInstance(field.getChildren().get(1))) return type.cast(field.getChildren().get(1));
        return null;
    }

    private Button findButton(VBox form, String text) {
        for (Node node : form.getChildren()) if (node instanceof Button button && (button.getText().equals(text) || button.getText().equals("Update product") && text.equals("Save product"))) return button;
        return null;
    }

    private void showNotice(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.getStyleClass().remove("status-error");
            statusLabel.getStyleClass().add("status-success");
        }
    }

    private void showFatal(String title, String detail) {
        Alert alert = new Alert(Alert.AlertType.ERROR, detail, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
        stage.close();
    }

    @Override
    public void stop() {
        if (service != null) service.clearCart();
    }

    public static void main(String[] args) { launch(args); }
}
