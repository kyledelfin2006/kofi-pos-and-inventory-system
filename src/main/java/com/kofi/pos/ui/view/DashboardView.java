package com.kofi.pos.ui.view; // One class per screen lives in this package.

import com.kofi.pos.model.Activity;                  // Row type of the activity table.
import com.kofi.pos.ui.common.ProductTables;           // Icon cell.
import com.kofi.pos.model.RestockAlert;              // Row type of the restock table.
import com.kofi.pos.service.PosService;              // Business operations.
import javafx.beans.property.ReadOnlyIntegerWrapper;  // Number cell value.
import javafx.beans.property.ReadOnlyStringWrapper;   // Text cell value.
import javafx.collections.FXCollections;              // Creates observable lists for tables.
import javafx.scene.Node;                             // Base visual type.
import javafx.scene.control.Button;                   // Button.
import javafx.scene.control.Label;                    // Text.
import javafx.scene.control.TableColumn;              // Table column.
import javafx.scene.control.TableView;                // Table.
import javafx.scene.layout.HBox;                      // Horizontal layout.
import javafx.scene.layout.Priority;                  // Grow priority.
import javafx.scene.layout.VBox;                      // Vertical layout.
import java.time.LocalDate;                           // Today's date.
import java.time.format.DateTimeFormatter;            // Time formatting.
import java.util.List;                                // List.of for columns.
import java.util.function.Consumer;                   // Callback taking one argument.
import static com.kofi.pos.ui.common.UiSupport.*;    // page(), metric(), panel(), peso() ...

/** Builds the daily totals, restock queue, and recent activity screen. */
public final class DashboardView {
    private final PosService service;          // Where all data comes from.
    private final Consumer<String> openStock;  // Callback that opens the Stock screen for a SKU.

    public DashboardView(PosService service, Consumer<String> openStock) {
        this.service = service;     // Store the service for later use.
        this.openStock = openStock; // Store the navigation callback.
    }

    /** Creates the screen's visual tree. */
    public Node build() {
        VBox root = page("Store overview", "A quick view of today’s sales and the stock that needs attention.");
        LocalDate today = LocalDate.now(); // "Today" for the metrics.
        HBox metrics = new HBox(0,         // Three tiles side by side with no gap.
                metric("Sales today", peso(service.salesTotal(today, today)), "Completed receipts only"),
                metric("Transactions", Integer.toString(service.salesCount(today, today)), "Today"),
                metric("Inventory at cost", peso(service.inventoryCostValue()), "Current on-hand stock")); // Uses recursion.
        metrics.getStyleClass().add("metric-strip");
        for (Node child : metrics.getChildren()) HBox.setHgrow(child, Priority.ALWAYS); // Tiles share the width equally.

        // ---- Restock queue table (data comes from the QUEUE in PosService) ----
        TableView<RestockAlert> alerts = new TableView<>();
        TableColumn<RestockAlert, String> alertName = new TableColumn<>("Product");
        alertName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().productName()));
        alertName.setCellFactory(column -> ProductTables.iconCell(alert -> service.findProduct(alert.sku()))); // Icon beside each alert.
        TableColumn<RestockAlert, String> alertSku = new TableColumn<>("SKU");
        alertSku.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().sku()));
        TableColumn<RestockAlert, Number> alertQty = new TableColumn<>("On hand");
        alertQty.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().quantity()));
        TableColumn<RestockAlert, Number> reorder = new TableColumn<>("Reorder at");
        reorder.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().reorderLevel()));
        alerts.getColumns().addAll(List.of(alertName, alertSku, alertQty, reorder));       // Attach columns.
        alerts.setItems(FXCollections.observableArrayList(service.restockAlerts()));       // Fill rows (FIFO order).
        alerts.setPlaceholder(new Label("Stock levels look good. No restock alerts."));    // Shown when empty.
        alerts.setPrefHeight(300);
        Button restock = new Button("Receive next restock");
        restock.getStyleClass().add("secondary-button");
        restock.setDisable(service.nextRestockAlert().isEmpty()); // Disabled when the queue is empty.
        restock.setOnAction(event -> {
            // Peek at the FRONT of the queue and jump to the Stock screen with that product preselected.
            service.nextRestockAlert().ifPresent(alert -> openStock.accept(alert.sku()));
        });
        VBox alertPanel = panel("Restock queue · " + service.pendingRestockCount(), alerts, restock);

        // ---- Recent activity table (data comes from the STACK in PosService) ----
        TableView<Activity> activity = new TableView<>();
        TableColumn<Activity, String> when = new TableColumn<>("Time");
        when.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().occurredAt().format(DateTimeFormatter.ofPattern("h:mm a"))));
        TableColumn<Activity, String> what = new TableColumn<>("Activity");
        what.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().action()));
        TableColumn<Activity, String> detail = new TableColumn<>("Detail");
        detail.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().detail()));
        activity.getColumns().addAll(List.of(when, what, detail));
        activity.setItems(FXCollections.observableArrayList(service.recentActivities())); // Newest (stack top) first.
        activity.setPlaceholder(new Label("Recent sales and stock actions will appear here."));
        activity.setPrefHeight(300);
        VBox activityPanel = panel("Recent activity", activity);
        HBox lower = new HBox(18, alertPanel, activityPanel);     // Two panels side by side.
        HBox.setHgrow(alertPanel, Priority.ALWAYS);               // Both stretch equally.
        HBox.setHgrow(activityPanel, Priority.ALWAYS);
        root.getChildren().addAll(metrics, lower);                // Add everything to the page.
        return root;
    }
}
