package com.cc104.pos.ui;

import com.cc104.pos.model.Sale;
import com.cc104.pos.service.PosService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import static com.cc104.pos.ui.UiSupport.*;

/** Builds date-filtered sales totals and current stock valuation. */
public final class ReportsView {
    private final PosService service;

    public ReportsView(PosService service) {
        this.service = service;
    }

    public Node build() {
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
}
