package com.kofi.pos.ui.common; // Shared UI helpers.

import com.kofi.pos.model.Product;       // Item shown on a tile.
import javafx.application.Platform;       // Used to focus the first size button after the dialog opens.
import javafx.geometry.Pos;               // Alignment.
import javafx.scene.Node;                 // Base visual type.
import javafx.scene.control.Button;       // Tiles and size buttons are real buttons: keyboard and screen-reader friendly.
import javafx.scene.control.ButtonType;   // Cancel button of the size picker.
import javafx.scene.control.Dialog;       // Size picker window.
import javafx.scene.control.Label;        // Text.
import javafx.scene.layout.FlowPane;      // Wrapping grid.
import javafx.scene.layout.HBox;          // Horizontal layout.
import javafx.scene.layout.VBox;          // Vertical layout.

import java.util.ArrayList;               // Lists.
import java.util.LinkedHashMap;           // Map that keeps insertion order.
import java.util.List;                    // List interface.
import java.util.Map;                     // Map interface.
import java.util.Optional;                // "Maybe a choice" from the dialog.
import java.util.function.Consumer;       // Callback.

import static com.kofi.pos.ui.common.UiSupport.peso; // Money text.

/**
 * The picture grid used at checkout. One tile per menu item: drinks that come in several sizes share one tile and
 * open a size picker, single items are added straight away.
 */
public final class ProductTiles {
    private static final double TILE_WIDTH = 148; // Fixed width so tiles line up in rows.
    private static final double TILE_HEIGHT = 226; // Icon + two-line name + price + stock note + padding.
    private static final double ICON_SIZE = 124;  // Picture inside a tile.

    private ProductTiles() { } // Utility class.

    /** Builds the wrapping grid. {@code onPick} receives the exact product (size included) the cashier chose. */
    public static FlowPane grid(List<Product> products, Consumer<Product> onPick) {
        FlowPane grid = new FlowPane(12, 12);          // 12 px gaps both ways.
        grid.getStyleClass().add("tile-grid");
        for (List<Product> group : groupByName(products).values()) grid.getChildren().add(tile(group, onPick));
        return grid;
    }

    /** Puts the sizes of one drink together (same name), keeping the order the products arrived in. */
    private static Map<String, List<Product>> groupByName(List<Product> products) {
        Map<String, List<Product>> groups = new LinkedHashMap<>();
        for (Product product : products) groups.computeIfAbsent(product.name(), name -> new ArrayList<>()).add(product);
        return groups;
    }

    /** One tile: icon, name, price and a stock note. */
    private static Button tile(List<Product> group, Consumer<Product> onPick) {
        Product first = group.get(0);
        boolean single = group.size() == 1;                                       // Single item or a drink with sizes?
        boolean allOut = group.stream().allMatch(p -> p.qtyOnHand() == 0);        // Nothing left to sell.
        boolean anyLow = group.stream().anyMatch(p -> p.qtyOnHand() <= p.reorderLevel());

        Label name = new Label(first.name());
        name.getStyleClass().add("tile-name");
        name.setWrapText(true);
        name.setPrefSize(ICON_SIZE, 34);                                          // Room for two lines so prices align.
        name.setMinHeight(34);
        name.setMaxSize(ICON_SIZE, 34);
        name.setAlignment(Pos.TOP_CENTER);
        name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Product cheapest = first;                                                 // "from" price for drinks with sizes.
        for (Product p : group) if (p.sellingPrice().compareTo(cheapest.sellingPrice()) < 0) cheapest = p;
        Label price = new Label((single ? "" : "from ") + peso(cheapest.sellingPrice()));
        price.getStyleClass().add("tile-price");

        String stockText = allOut ? "Out of stock"
                : single && anyLow ? "Low · " + first.qtyOnHand() + " left"
                : !single && anyLow ? "Some sizes low" : "";                      // Healthy stock shows nothing.
        Label stock = new Label(stockText);
        stock.getStyleClass().add(allOut ? "stock-out" : "stock-low");
        stock.setMinHeight(18);                                                   // Keep the height even when empty.
        stock.setVisible(!stockText.isEmpty());

        VBox content = new VBox(5, AssetLoader.icon(AssetLoader.product(first, ICON_SIZE * 2), ICON_SIZE), name, price, stock);
        content.setAlignment(Pos.TOP_CENTER);

        Button tile = new Button();
        tile.setGraphic(content);
        tile.getStyleClass().add("product-tile");
        tile.setPrefSize(TILE_WIDTH, TILE_HEIGHT);                                // Every tile is the same size.
        tile.setMinSize(TILE_WIDTH, TILE_HEIGHT);
        tile.setMaxSize(TILE_WIDTH, TILE_HEIGHT);
        tile.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        tile.setDisable(allOut);
        tile.setAccessibleText(first.name() + ", " + (single ? "" : "from ") + peso(cheapest.sellingPrice()) + (stockText.isEmpty() ? "" : ", " + stockText));
        tile.setOnAction(event -> {
            if (single) onPick.accept(first);                                     // One product: add it.
            else pickSize(tile, group).ifPresent(onPick);                         // Several sizes: ask which.
        });
        return tile;
    }

    /** Small window listing every size of a drink with its price and stock. */
    private static Optional<Product> pickSize(Node owner, List<Product> group) {
        Product first = group.get(0);
        Dialog<Product> dialog = new Dialog<>();
        dialog.initOwner(owner.getScene().getWindow());                           // Centre over the main window.
        dialog.setTitle("Choose a size");
        dialog.getDialogPane().getStylesheets().add(ProductTiles.class.getResource("/style.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);           // Cancel button; Esc also closes.

        Label title = new Label(first.name());
        title.getStyleClass().add("section-heading");
        HBox head = new HBox(12, AssetLoader.icon(AssetLoader.product(first, 128), 64), title);
        head.setAlignment(Pos.CENTER_LEFT);

        FlowPane options = new FlowPane(10, 10);
        options.setPrefWrapLength(300);                                           // Two size buttons per row.
        List<Button> buttons = new ArrayList<>();
        for (Product product : group) {
            Label label = new Label(sizeLabel(product));
            label.getStyleClass().add("size-name");
            Label cost = new Label(peso(product.sellingPrice()));
            cost.getStyleClass().add("tile-price");
            Label left = new Label(product.qtyOnHand() == 0 ? "Out of stock"
                    : product.qtyOnHand() <= product.reorderLevel() ? product.qtyOnHand() + " left" : "");
            left.getStyleClass().add(product.qtyOnHand() == 0 ? "stock-out" : "stock-low");
            left.setMinHeight(18);
            left.setVisible(!left.getText().isEmpty());                          // Keep the height, hide the empty pill.
            VBox text = new VBox(3, label, cost, left);
            text.setAlignment(Pos.CENTER_LEFT);
            Button button = new Button();
            button.setGraphic(text);
            button.getStyleClass().add("size-button");
            button.setPrefWidth(142);
            button.setDisable(product.qtyOnHand() == 0);
            button.setAccessibleText(sizeLabel(product) + ", " + peso(product.sellingPrice()));
            button.setOnAction(event -> { dialog.setResult(product); dialog.close(); });
            buttons.add(button);
            options.getChildren().add(button);
        }
        VBox body = new VBox(16, head, options);
        body.setPrefWidth(310);
        dialog.getDialogPane().setContent(body);
        Platform.runLater(() -> buttons.stream().filter(b -> !b.isDisabled()).findFirst().ifPresent(Button::requestFocus));
        return dialog.showAndWait();
    }

    /** "Hot · 8 oz", "Iced · 16 oz", or "Regular" when the product has neither. */
    private static String sizeLabel(Product product) {
        String size = product.sizeOz() > 0 ? product.sizeOz() + " oz" : "";
        String text = (product.variant() + (product.variant().isEmpty() || size.isEmpty() ? "" : " · ") + size).trim();
        return text.isEmpty() ? "Regular" : text;
    }
}
