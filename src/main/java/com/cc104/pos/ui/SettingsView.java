package com.cc104.pos.ui;

import com.cc104.pos.model.Settings;
import com.cc104.pos.service.PosService;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.util.function.Consumer;
import static com.cc104.pos.ui.UiSupport.*;

/** Builds the store name, address, and receipt footer editor. */
public final class SettingsView {
    private final PosService service;
    private final Consumer<String> notice;
    private final Consumer<String> navigate;
    private final Runnable settingsSaved;

    public SettingsView(PosService service, Consumer<String> notice, Consumer<String> navigate, Runnable settingsSaved) {
        this.service = service;
        this.notice = notice;
        this.navigate = navigate;
        this.settingsSaved = settingsSaved;
    }
    public Node build() {
        VBox root = page("Store settings", "These details appear on the receipt preview.");
        Settings existing = service.settings();
        TextField name = new TextField(existing.storeName());
        name.setTextFormatter(limitFormatter(80));
        TextField address = new TextField(existing.storeAddress());
        address.setTextFormatter(limitFormatter(150));
        TextArea footer = new TextArea(existing.receiptFooter());
        footer.setWrapText(true);
        footer.setPrefRowCount(5);
        footer.setTextFormatter(limitFormatter(200));
        Label count = counter(footer, 200);
        Label error = errorLabel();
        Button save = new Button("Save settings");
        save.getStyleClass().add("primary-button");
        save.setDefaultButton(true);
        VBox form = new VBox(12, field("Store name *", name), field("Store address", address),
                field("Receipt footer", footer), count, error, save);
        form.getStyleClass().add("surface-panel");
        form.setPadding(new Insets(22));
        form.setMaxWidth(680);
        Runnable validate = () -> {
            boolean valid = !name.getText().trim().isEmpty();
            showFieldError(name, error, valid ? "" : "Store name is required.");
            save.setDisable(!valid);
        };
        name.textProperty().addListener((obs, old, value) -> validate.run());
        name.focusedProperty().addListener((obs, was, focused) -> { if (!focused) name.setText(name.getText().trim()); });
        address.focusedProperty().addListener((obs, was, focused) -> { if (!focused) address.setText(address.getText().trim()); });
        footer.focusedProperty().addListener((obs, was, focused) -> { if (!focused) footer.setText(footer.getText().trim()); });
        save.setOnAction(event -> {
            try {
                service.updateSettings(new Settings(name.getText(), address.getText(), footer.getText()));
                notice.accept("Store settings saved");
                settingsSaved.run();
            } catch (IllegalArgumentException | IOException e) { error.setText(e.getMessage()); }
        });
        form.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ESCAPE) navigate.accept("Settings"); });
        validate.run();
        root.getChildren().add(form);
        return root;
    }
}
