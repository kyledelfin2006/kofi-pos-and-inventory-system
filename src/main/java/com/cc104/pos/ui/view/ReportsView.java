package com.cc104.pos.ui.view; // One class per screen.

import com.cc104.pos.model.Sale;                      // Row type.
import com.cc104.pos.service.PosService;              // Business operations.
import javafx.beans.property.ReadOnlyStringWrapper;   // Text cell value.
import javafx.collections.FXCollections;              // Observable lists.
import javafx.geometry.Pos;                           // Alignment constants.
import javafx.scene.Node;                             // Base visual type.
import javafx.scene.control.Button;                   // Button.
import javafx.scene.control.Label;                    // Text.
import javafx.scene.control.TableColumn;              // Column.
import javafx.scene.control.TableView;                // Table.
import javafx.scene.control.TextField;                // Input.
import javafx.scene.input.KeyCode;                    // Keys.
import javafx.scene.layout.GridPane;                  // Grid layout.
import javafx.scene.layout.HBox;                      // Horizontal layout.
import javafx.scene.layout.VBox;                      // Vertical layout.
import java.time.LocalDate;                           // Dates.
import java.time.format.DateTimeParseException;       // Bad date text.
import java.util.ArrayList;                           // Lists.
import java.util.List;                                // List.of.
import static com.cc104.pos.ui.common.UiSupport.*;    // Shared helpers.

/** Builds date-filtered sales totals and current stock valuation. */
public final class ReportsView {
    private final PosService service; // Business operations.

    public ReportsView(PosService service) {
        this.service = service;
    }

    /** Creates the screen's visual tree. */
    public Node build() {
        VBox root = page("Reports", "Review completed sales and stock valuation for a date range.");
        String today = LocalDate.now().toString(); // yyyy-MM-dd, default for both filters.
        TextField from = new TextField(today);
        from.setPromptText("YYYY-MM-DD");
        from.setTextFormatter(dateFormatter());    // Auto-inserts dashes.
        trimOnBlur(from);
        TextField to = new TextField(today);
        to.setPromptText("YYYY-MM-DD");
        to.setTextFormatter(dateFormatter());
        trimOnBlur(to);
        Label error = errorLabel();
        TextField salesAmount = readOnlyField();   // Result fields (read-only).
        TextField count = readOnlyField();
        TextField stockValue = readOnlyField();
        Button apply = new Button("Run report");
        apply.getStyleClass().add("primary-button");
        HBox filters = new HBox(12, field("Date from *", from), field("Date to *", to), apply);
        filters.setAlignment(Pos.BOTTOM_LEFT);
        Label salesTitle = new Label("Sales in range");
        salesTitle.getStyleClass().add("section-heading");
        GridPane totals = new GridPane();          // Label/value rows.
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

        Runnable run = () -> { // Runs the report with the dates currently typed.
            try {
                LocalDate first = LocalDate.parse(from.getText()); // Throws DateTimeParseException if malformed.
                LocalDate last = LocalDate.parse(to.getText());
                if (first.isAfter(LocalDate.now()) || last.isAfter(LocalDate.now())) throw new IllegalArgumentException("Future dates are not allowed.");
                if (first.isAfter(last)) throw new IllegalArgumentException("Date from must be on or before date to.");
                ArrayList<Sale> filtered = new ArrayList<>();
                for (Sale sale : service.sortSalesByDate()) if (sale.status().equals("COMPLETED") // Newest first (insertion sort).
                        && !sale.soldAt().toLocalDate().isBefore(first) && !sale.soldAt().toLocalDate().isAfter(last)) filtered.add(sale);
                table.setItems(FXCollections.observableArrayList(filtered)); // Show matching receipts.
                salesAmount.setText(peso(service.salesTotal(first, last)));
                count.setText(Integer.toString(service.salesCount(first, last)));
                stockValue.setText(peso(service.inventoryCostValue()));      // Recursive total.
                error.setText("");
                setInvalid(from, false);
                setInvalid(to, false);
                apply.setDisable(false);
            } catch (DateTimeParseException e) { error.setText("Use YYYY-MM-DD for both dates."); }
            catch (IllegalArgumentException e) { error.setText(e.getMessage()); }
        };
        apply.setOnAction(event -> run.run());
        apply.setDefaultButton(true);

        Runnable validateDates = () -> { // Live validation while typing; disables the button when invalid.
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
            if (event.getCode() == KeyCode.ESCAPE) { // Esc resets both dates to today and re-runs.
                from.setText(today);
                to.setText(today);
                run.run();
            }
        });
        VBox summary = panel("Report summary", salesTitle, totals, error);
        root.getChildren().addAll(filters, summary, table);
        validateDates.run(); // Initial validation.
        run.run();           // Initial report for today.
        return root;
    }
}
