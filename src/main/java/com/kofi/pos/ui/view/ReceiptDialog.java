package com.kofi.pos.ui.view; // One class per screen/dialog.

import com.kofi.pos.model.Sale;             // The receipt header to show.
import com.kofi.pos.model.SaleLine;         // Receipt lines.
import com.kofi.pos.service.PosService;     // Looks up lines and store settings.
import javafx.geometry.Insets;               // Padding.
import javafx.scene.control.*;               // Dialog, Label, Separator, ScrollPane, ButtonType, ButtonBar.
import javafx.scene.layout.VBox;             // Vertical layout.
import javafx.stage.Modality;                // Blocks the parent window while open.
import javafx.stage.Stage;                   // The parent window.

import static com.kofi.pos.ui.common.UiSupport.DATE_TIME; // Shared date format.
import static com.kofi.pos.ui.common.UiSupport.peso;      // Shared money format.

/** Renders a saved sale as a read-only receipt preview. */
public final class ReceiptDialog {
    private final PosService service; // For store settings and sale lines.
    private final Stage owner;        // Parent window of the dialog.

    public ReceiptDialog(PosService service, Stage owner) {
        this.service = service;
        this.owner = owner;
    }

    /** Opens the dialog and waits until the user closes it. */
    public void show(Sale sale) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);                          // Attach to the main window.
        dialog.initModality(Modality.WINDOW_MODAL);       // Main window is blocked meanwhile.
        dialog.setTitle("Receipt · " + sale.receiptNo());
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE)); // One Close button.

        VBox receipt = new VBox(9);                       // The "paper".
        receipt.getStyleClass().add("receipt-paper");
        receipt.setPadding(new Insets(24));
        Label store = new Label(service.settings().storeName());
        store.getStyleClass().add("receipt-store");
        Label address = new Label(service.settings().storeAddress());
        Label number = new Label(sale.receiptNo());
        number.getStyleClass().add("receipt-number");
        receipt.getChildren().addAll(store, address, number, new Label(sale.soldAt().format(DATE_TIME)),
                new Label("Cashier: " + sale.cashier()), new Separator()); // Header block.
        for (SaleLine line : service.saleLines(sale.receiptNo())) { // One text line per purchased item.
            receipt.getChildren().add(new Label(line.productName() + "  ×  " + line.quantity() + "  ·  " + peso(line.lineTotal())));
        }
        receipt.getChildren().addAll(new Separator(), new Label("Subtotal  " + peso(sale.subtotal())),
                new Label("Discount  " + peso(sale.discount())), new Label("TOTAL  " + peso(sale.total())),
                new Label("Cash  " + peso(sale.cashTendered())), new Label("Change  " + peso(sale.changeAmount()))); // Totals block.
        if (sale.status().equals("VOID")) receipt.getChildren().add(new Label("VOID · " + sale.voidReason())); // Void banner.
        if (!service.settings().receiptFooter().isBlank()) receipt.getChildren().add(new Label(service.settings().receiptFooter())); // Footer.

        ScrollPane scroll = new ScrollPane(receipt);      // Scrolls if the receipt is long.
        scroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.showAndWait();                             // Block here until closed.
    }
}
