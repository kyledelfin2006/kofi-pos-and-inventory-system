package com.cc104.pos.ui;

import com.cc104.pos.model.Sale;
import com.cc104.pos.model.SaleLine;
import com.cc104.pos.service.PosService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import static com.cc104.pos.ui.UiSupport.DATE_TIME;
import static com.cc104.pos.ui.UiSupport.peso;

/** Renders a saved sale as a read-only receipt preview. */
public final class ReceiptDialog {
    private final PosService service;
    private final Stage owner;

    public ReceiptDialog(PosService service, Stage owner) {
        this.service = service;
        this.owner = owner;
    }

    public void show(Sale sale) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
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
        receipt.getChildren().addAll(store, address, number, new Label(sale.soldAt().format(DATE_TIME)),
                new Label("Cashier: " + sale.cashier()), new Separator());
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
}
