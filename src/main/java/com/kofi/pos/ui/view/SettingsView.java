package com.kofi.pos.ui.view; // One class per screen.

import com.kofi.pos.model.Settings;          // Store settings record.
import com.kofi.pos.service.PosService;      // Business operations.
import javafx.geometry.Insets;                // Padding.
import javafx.scene.Node;                     // Base visual type.
import javafx.scene.control.Alert;            // Standard pop-up dialog.
import javafx.scene.control.Button;           // Button.
import javafx.scene.control.ButtonType;       // OK / Cancel button definitions.
import javafx.scene.control.Label;            // Text.
import javafx.scene.control.TextArea;         // Multi-line input.
import javafx.scene.control.TextField;        // Single-line input.
import javafx.scene.input.KeyCode;            // Keyboard keys.
import javafx.scene.layout.VBox;              // Vertical layout.
import javafx.stage.Stage;                    // Parent window for the confirmation dialog.
import java.io.IOException;                   // Storage failure.
import java.util.function.Consumer;           // One-argument callback.
import static com.kofi.pos.ui.common.UiSupport.*; // Shared UI helpers.

/** Builds the store settings editor and the "reset data to default" control. */
public final class SettingsView {
    private final PosService service;          // Business operations.
    private final Stage stage;                 // Owner of the confirmation dialog.
    private final Consumer<String> notice;     // Shows a message in the top bar.
    private final Consumer<String> navigate;   // Opens another module by name.
    private final Runnable settingsSaved;      // Refreshes title/sidebar after data changes.

    public SettingsView(PosService service, Stage stage, Consumer<String> notice,
                        Consumer<String> navigate, Runnable settingsSaved) {
        this.service = service;
        this.stage = stage;
        this.notice = notice;
        this.navigate = navigate;
        this.settingsSaved = settingsSaved;
    }

    /** Creates the screen's visual tree. */
    public Node build() {
        VBox root = page("Store settings", "These details appear on the receipt preview.");
        root.getChildren().addAll(settingsForm(), resetPanel()); // Two cards: the editor and the reset control.
        return root;
    }

    /** The store-name / address / footer editor. */
    private VBox settingsForm() {
        Settings existing = service.settings(); // Current values to prefill.
        TextField name = new TextField(existing.storeName());
        name.setTextFormatter(limitFormatter(80));          // Max 80 characters.
        TextField address = new TextField(existing.storeAddress());
        address.setTextFormatter(limitFormatter(150));
        TextArea footer = new TextArea(existing.receiptFooter());
        footer.setWrapText(true);
        footer.setPrefRowCount(5);
        footer.setTextFormatter(limitFormatter(200));
        Label count = counter(footer, 200);                 // "n / 200" counter.
        Label error = errorLabel();
        Button save = new Button("Save settings");
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);                        // Enter key presses it.
        VBox form = new VBox(12, field("Store name *", name), field("Store address", address),
                field("Receipt footer", footer), count, error, save);
        form.getStyleClass().add("surface-panel");
        form.setPadding(new Insets(22));
        form.setMaxWidth(680);
        Runnable validate = () -> {                         // Re-checks the form after each edit.
            boolean valid = !name.getText().trim().isEmpty();
            showFieldError(name, error, valid ? "" : "Store name is required.");
            save.setDisable(!valid);                        // Cannot save without a store name.
        };
        name.textProperty().addListener((obs, old, value) -> validate.run());
        name.focusedProperty().addListener((obs, was, focused) -> { if (!focused) name.setText(name.getText().trim()); });
        address.focusedProperty().addListener((obs, was, focused) -> { if (!focused) address.setText(address.getText().trim()); });
        footer.focusedProperty().addListener((obs, was, focused) -> { if (!focused) footer.setText(footer.getText().trim()); });
        save.setOnAction(event -> {
            try {
                service.updateSettings(new Settings(name.getText(), address.getText(), footer.getText())); // Validate + save.
                notice.accept("Store settings saved");
                settingsSaved.run();                         // Refresh title and sidebar.
            } catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); } // Show the problem.
        });
        form.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) navigate.accept("Settings"); }); // Esc = reload screen.
        validate.run();
        return form;
    }

    /** The danger-zone card with the reset button. */
    private VBox resetPanel() {
        Label description = new Label("Restore the original sample data (products, users, store settings) "
                + "from the default CSV files. All sales, receipts, stock movements and edits are erased.");
        description.setWrapText(true);
        description.getStyleClass().add("muted-text");
        Label error = errorLabel();
        Button reset = new Button("Reset data to default");
        reset.getStyleClass().add("danger-button");
        reset.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This permanently erases the current data and restores the default data. Continue?",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.initOwner(stage);                       // Centre over the main window.
            confirm.setTitle("Reset data");
            confirm.setHeaderText("Reset all data to default?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return; // User backed out.
            try {
                service.resetToDefaults();                  // Restore default CSVs and reload memory.
                notice.accept("Data reset to default");
                settingsSaved.run();                        // Refresh sidebar, title and this screen.
            } catch (IOException e) { error.setText("Could not reset data: " + e.getMessage()); }
        });
        VBox panel = panel("Reset data", description, error, reset);
        panel.setMaxWidth(680);
        return panel;
    }
}
