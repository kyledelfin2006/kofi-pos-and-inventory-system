package com.cc104.pos.ui;

import com.cc104.pos.model.Sale;
import com.cc104.pos.model.SaleLine;
import com.cc104.pos.service.PosService;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import static com.cc104.pos.ui.UiSupport.*;

/** Builds receipt search, sorting, detail, preview, and void actions. */
public final class SalesView {
    private final PosService service;
    private final Stage stage;
    private final Consumer<String> notice;

    public SalesView(PosService service, Stage stage, Consumer<String> notice) {
        this.service = service;
        this.stage = stage;
        this.notice = notice;
    }
    public Node build() {
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
        reason.setTextFormatter(limitFormatter(150));
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
                notice.accept("Receipt voided and stock restored");
            } catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
        });
        Button receiptView = new Button("Receipt preview");
        receiptView.getStyleClass().add("secondary-button");
        receiptView.setOnAction(event -> new ReceiptDialog(service, stage).show(sale));
        detail.getChildren().addAll(title, number, date, state, lines, totals, voidReason);
        if (!sale.status().equals("VOID")) detail.getChildren().addAll(field("Void reason *", reason), count, error, voidButton);
        detail.getChildren().add(receiptView);
    }
}
