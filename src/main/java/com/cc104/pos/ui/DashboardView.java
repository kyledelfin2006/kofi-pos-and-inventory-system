package com.cc104.pos.ui;

import com.cc104.pos.model.Activity;
import com.cc104.pos.model.RestockAlert;
import com.cc104.pos.service.PosService;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import static com.cc104.pos.ui.UiSupport.*;

/** Builds the daily totals, restock queue, and recent activity screen. */
public final class DashboardView {
    private final PosService service;
    private final Consumer<String> openStock;

    public DashboardView(PosService service, Consumer<String> openStock) {
        this.service = service;
        this.openStock = openStock;
    }
    public Node build() {
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
            service.nextRestockAlert().ifPresent(alert -> openStock.accept(alert.sku()));
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
}
