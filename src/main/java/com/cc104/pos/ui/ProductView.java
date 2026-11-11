package com.cc104.pos.ui;

import com.cc104.pos.model.Product;
import com.cc104.pos.service.PosService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import static com.cc104.pos.ui.UiSupport.*;

/** Builds product search, sorting, editing, and safe deletion controls. */
public final class ProductView {
    private final PosService service;
    private final Stage stage;
    private final Consumer<String> notice;
    private String productEditSku;

    public ProductView(PosService service, Stage stage, Consumer<String> notice) {
        this.service = service;
        this.stage = stage;
        this.notice = notice;
    }
    public Node build() {
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

        TableView<Product> table = ProductTables.create(true);
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
                notice.accept(productEditSku == null ? "Product added" : "Product updated");
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
                    notice.accept("Product deleted or deactivated");
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
}
