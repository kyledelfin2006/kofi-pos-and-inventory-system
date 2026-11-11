package com.cc104.pos.ui;

import com.cc104.pos.service.PosService;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared JavaFX formatting and layout helpers used by the module views. */
public final class UiSupport {
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US);
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.US);

    static {
        MONEY.setMinimumFractionDigits(2);
        MONEY.setMaximumFractionDigits(2);
    }
    static VBox page(String heading, String description) {
        Label title = new Label(heading);
        title.getStyleClass().add("page-heading");
        Label sub = new Label(description);
        sub.getStyleClass().add("page-description");
        VBox header = new VBox(6, title, sub);
        header.setPadding(new Insets(4, 0, 20, 0));
        VBox page = new VBox(18, header);
        page.setPadding(new Insets(26, 30, 36, 30));
        page.getStyleClass().add("page-content");
        return page;
    }
    static VBox metric(String labelText, String valueText, String noteText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("metric-label");
        Label value = new Label(valueText);
        value.getStyleClass().add("metric-value");
        Label note = new Label(noteText);
        note.getStyleClass().add("muted-small");
        VBox box = new VBox(8, label, value, note);
        box.getStyleClass().add("metric-block");
        return box;
    }
    static VBox panel(String title, Node... contents) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        VBox panel = new VBox(14, heading);
        panel.getChildren().addAll(contents);
        panel.getStyleClass().add("surface-panel");
        panel.setPadding(new Insets(18));
        return panel;
    }

    public static VBox field(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(5, label, control);
        box.setFillWidth(true);
        if (control instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    static Label errorLabel() {
        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        return error;
    }

    static Label counter(TextArea area, int max) {
        Label count = new Label(area.getText().length() + " / " + max);
        count.getStyleClass().add("counter-label");
        area.textProperty().addListener((obs, old, value) -> count.setText(value.length() + " / " + max));
        return count;
    }

    static TextField readOnlyField() {
        TextField field = new TextField();
        field.setEditable(false);
        field.getStyleClass().add("readonly-field");
        return field;
    }

    public static TextFormatter<String> limitFormatter(int max) {
        return new TextFormatter<>(change -> change.getControlNewText().length() <= max ? change : null);
    }

    static TextFormatter<String> integerFormatter() {
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            return text.isEmpty() || text.matches("\\d{0,9}") ? change : null;
        });
    }

    static TextFormatter<String> moneyFormatter() {
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            return text.isEmpty() || text.matches("\\d{0,9}(\\.\\d{0,2})?") ? change : null;
        });
    }

    static TextFormatter<String> dateFormatter() {
        return new TextFormatter<>(change -> {
            String raw = change.getControlNewText().replace("-", "");
            if (!raw.matches("\\d{0,8}")) return null;
            StringBuilder formatted = new StringBuilder(raw);
            if (raw.length() > 4) formatted.insert(4, '-');
            if (raw.length() > 6) formatted.insert(7, '-');
            change.setRange(0, change.getControlText().length());
            change.setText(formatted.toString());
            change.setCaretPosition(formatted.length());
            change.setAnchor(formatted.length());
            return change;
        });
    }

    static void bindPesoBlur(TextField field) {
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                String raw = field.getText().replace("₱", "").replace(",", "").trim();
                replaceText(field, raw);
            } else {
                BigDecimal value = parsedMoney(field.getText());
                if (value != null && !field.getText().isBlank()) replaceText(field, peso(value));
            }
        });
    }

    static void trimOnBlur(TextField field) {
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) field.setText(field.getText().trim());
        });
    }

    private static void replaceText(TextField field, String value) {
        TextFormatter<?> existing = field.getTextFormatter();
        field.setTextFormatter(null);
        field.setText(value);
        field.setTextFormatter(existing);
    }

    static BigDecimal parsedMoney(String text) {
        if (text == null || text.isBlank()) return null;
        try { return new BigDecimal(text.replace("₱", "").replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    static String peso(BigDecimal value) {
        return "₱" + MONEY.format(PosService.money(value));
    }

    public static void showFieldError(TextField field, Label error, String message) {
        error.setText(message == null ? "" : message);
        setInvalid(field, message != null && !message.isBlank());
    }

    static void showFieldError(TextArea field, Label error, String message) {
        error.setText(message == null ? "" : message);
        setInvalid(field, message != null && !message.isBlank());
    }

    static void setInvalid(Node field, boolean invalid) {
        field.getStyleClass().remove("invalid-field");
        if (invalid) field.getStyleClass().add("invalid-field");
    }

    static void setRequiredLabel(VBox form, String fieldName, boolean required) {
        for (Node node : form.getChildren()) {
            if (node instanceof VBox field && !field.getChildren().isEmpty() && field.getChildren().get(0) instanceof Label label
                    && label.getText().startsWith(fieldName)) {
                label.setText(required ? fieldName + " *" : fieldName);
            }
        }
    }

    static <T extends Node> T fieldControl(VBox form, String label, Class<T> type) {
        for (Node node : form.getChildren()) if (node instanceof VBox field && field.getChildren().size() > 1
                && field.getChildren().get(0) instanceof Label text && text.getText().startsWith(label)
                && type.isInstance(field.getChildren().get(1))) return type.cast(field.getChildren().get(1));
        return null;
    }

    static Button findButton(VBox form, String text) {
        for (Node node : form.getChildren()) if (node instanceof Button button && (button.getText().equals(text) || button.getText().equals("Update product") && text.equals("Save product"))) return button;
        return null;
    }
}
