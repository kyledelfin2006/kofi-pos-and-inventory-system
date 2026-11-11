package com.cc104.pos.ui.common; // Shared UI helpers used by every screen (no business rules here).

import com.cc104.pos.util.Money;               // Rounding for displayed money.
import javafx.geometry.Insets;                 // Padding values.
import javafx.scene.Node;                      // Base class of every visual element.
import javafx.scene.control.Button;            // Clickable button.
import javafx.scene.control.Label;             // Static text.
import javafx.scene.control.TextArea;          // Multi-line text input.
import javafx.scene.control.TextField;         // Single-line text input.
import javafx.scene.control.TextFormatter;     // Filters what the user may type.
import javafx.scene.layout.Region;             // Resizable layout node.
import javafx.scene.layout.VBox;               // Vertical stack layout.

import java.math.BigDecimal;                   // Exact decimals.
import java.text.NumberFormat;                 // Formats numbers with thousands separators.
import java.time.format.DateTimeFormatter;     // Formats dates and times.
import java.util.Locale;                       // Locale for formatting.

/** Shared JavaFX formatting and layout helpers used by the module views. */
public final class UiSupport {
    private UiSupport() { } // Utility class: nobody creates an instance.

    /** Standard date/time look, e.g. "Nov 12, 2026 · 3:05 PM". */
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US);
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(Locale.US); // 1,234.50 style.

    static { // Static initialiser: runs once when the class loads.
        MONEY.setMinimumFractionDigits(2); // Always show two decimals.
        MONEY.setMaximumFractionDigits(2);
    }

    /** Builds the standard page: a big heading, a grey description, and room for content. */
    public static VBox page(String heading, String description) {
        Label title = new Label(heading);                        // Main heading text.
        title.getStyleClass().add("page-heading");               // CSS class from style.css.
        Label sub = new Label(description);                      // Explanatory line.
        sub.getStyleClass().add("page-description");
        VBox header = new VBox(6, title, sub);                   // Stack heading above description, 6px apart.
        header.setPadding(new Insets(4, 0, 20, 0));              // Space below the header.
        VBox page = new VBox(18, header);                        // The page itself, with the header as first child.
        page.setPadding(new Insets(26, 30, 36, 30));             // Outer margins.
        page.getStyleClass().add("page-content");
        return page;
    }

    /** One statistic tile: small label, big value, small note. */
    public static VBox metric(String labelText, String valueText, String noteText) {
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

    /** A titled card that wraps the given content nodes. */
    public static VBox panel(String title, Node... contents) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-heading");
        VBox panel = new VBox(14, heading);          // Heading first.
        panel.getChildren().addAll(contents);        // Then everything the caller passed in.
        panel.getStyleClass().add("surface-panel");
        panel.setPadding(new Insets(18));
        return panel;
    }

    /** A label stacked above a control (text box, combo box, ...). */
    public static VBox field(String labelText, Node control) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(5, label, control);
        box.setFillWidth(true);                                            // Control stretches to full width.
        if (control instanceof Region region) region.setMaxWidth(Double.MAX_VALUE); // Allow it to grow.
        return box;
    }

    /** A red, wrapping label used to show validation messages. */
    public static Label errorLabel() {
        Label error = new Label();
        error.getStyleClass().add("field-error");
        error.setWrapText(true);
        return error;
    }

    /** A "12 / 200" character counter that updates as the user types. */
    public static Label counter(TextArea area, int max) {
        Label count = new Label(area.getText().length() + " / " + max);
        count.getStyleClass().add("counter-label");
        area.textProperty().addListener((obs, old, value) -> count.setText(value.length() + " / " + max)); // Refresh on each edit.
        return count;
    }

    /** A text field that displays a value but cannot be edited. */
    public static TextField readOnlyField() {
        TextField field = new TextField();
        field.setEditable(false);
        field.getStyleClass().add("readonly-field");
        return field;
    }

    /** Rejects any edit that would make the text longer than {@code max}. */
    public static TextFormatter<String> limitFormatter(int max) {
        return new TextFormatter<>(change -> change.getControlNewText().length() <= max ? change : null); // null = reject.
    }

    /** Accepts only digits (up to 9 of them). */
    public static TextFormatter<String> integerFormatter() {
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();                       // What the text would become.
            return text.isEmpty() || text.matches("\\d{0,9}") ? change : null; // Allow only digits.
        });
    }

    /** Accepts digits with an optional decimal point and at most two decimals. */
    public static TextFormatter<String> moneyFormatter() {
        return new TextFormatter<>(change -> {
            String text = change.getControlNewText();
            return text.isEmpty() || text.matches("\\d{0,9}(\\.\\d{0,2})?") ? change : null;
        });
    }

    /** Auto-inserts dashes so typing 20261112 becomes 2026-11-12. */
    public static TextFormatter<String> dateFormatter() {
        return new TextFormatter<>(change -> {
            String raw = change.getControlNewText().replace("-", ""); // Strip dashes, keep digits.
            if (!raw.matches("\\d{0,8}")) return null;                // More than 8 digits or a letter: reject.
            StringBuilder formatted = new StringBuilder(raw);
            if (raw.length() > 4) formatted.insert(4, '-');           // After the year.
            if (raw.length() > 6) formatted.insert(7, '-');           // After the month.
            change.setRange(0, change.getControlText().length());     // Replace the entire old text...
            change.setText(formatted.toString());                     // ...with the re-formatted text,
            change.setCaretPosition(formatted.length());              // and keep the cursor at the end.
            change.setAnchor(formatted.length());
            return change;
        });
    }

    /** Shows "₱1,234.00" when the field loses focus and raw digits when it gains focus. */
    public static void bindPesoBlur(TextField field) {
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) { // Entering the field: strip the formatting so it is easy to edit.
                String raw = field.getText().replace("₱", "").replace(",", "").trim();
                replaceText(field, raw);
            } else {       // Leaving the field: pretty-print valid amounts.
                BigDecimal value = parsedMoney(field.getText());
                if (value != null && !field.getText().isBlank()) replaceText(field, peso(value));
            }
        });
    }

    /** Removes leading/trailing spaces when the field loses focus. */
    public static void trimOnBlur(TextField field) {
        field.focusedProperty().addListener((obs, was, focused) -> {
            if (!focused) field.setText(field.getText().trim());
        });
    }

    /** Sets text while temporarily detaching the formatter (the formatter would reject "₱..." text). */
    private static void replaceText(TextField field, String value) {
        TextFormatter<?> existing = field.getTextFormatter(); // Remember the filter.
        field.setTextFormatter(null);                          // Remove it.
        field.setText(value);                                  // Set the text freely.
        field.setTextFormatter(existing);                      // Put the filter back.
    }

    /** Parses text like "₱1,250.50" to a BigDecimal, or null when it is blank or invalid. */
    public static BigDecimal parsedMoney(String text) {
        if (text == null || text.isBlank()) return null;
        try { return new BigDecimal(text.replace("₱", "").replace(",", "").trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /** Formats a value as pesos, e.g. "₱1,250.50". */
    public static String peso(BigDecimal value) {
        return "₱" + MONEY.format(Money.of(value));
    }

    /** Shows (or clears) a validation message for a text field and toggles its red outline. */
    public static void showFieldError(TextField field, Label error, String message) {
        error.setText(message == null ? "" : message);
        setInvalid(field, message != null && !message.isBlank());
    }

    /** Same as above, for multi-line text areas. */
    public static void showFieldError(TextArea field, Label error, String message) {
        error.setText(message == null ? "" : message);
        setInvalid(field, message != null && !message.isBlank());
    }

    /** Adds or removes the "invalid-field" CSS class (red outline). */
    public static void setInvalid(Node field, boolean invalid) {
        field.getStyleClass().remove("invalid-field"); // Remove first so it is never added twice.
        if (invalid) field.getStyleClass().add("invalid-field");
    }

    /** Adds or removes the " *" required marker on a form field's label. */
    public static void setRequiredLabel(VBox form, String fieldName, boolean required) {
        for (Node node : form.getChildren()) { // Look at each child of the form.
            if (node instanceof VBox field && !field.getChildren().isEmpty() && field.getChildren().get(0) instanceof Label label
                    && label.getText().startsWith(fieldName)) {
                label.setText(required ? fieldName + " *" : fieldName);
            }
        }
    }

    /** Finds the control inside a labelled field of a form, by the label's text and the control's type. */
    public static <T extends Node> T fieldControl(VBox form, String label, Class<T> type) {
        for (Node node : form.getChildren()) if (node instanceof VBox field && field.getChildren().size() > 1
                && field.getChildren().get(0) instanceof Label text && text.getText().startsWith(label)
                && type.isInstance(field.getChildren().get(1))) return type.cast(field.getChildren().get(1));
        return null; // Not found.
    }

    /** Finds a direct child button of the form by its text. */
    public static Button findButton(VBox form, String text) {
        for (Node node : form.getChildren()) if (node instanceof Button button && (button.getText().equals(text) || button.getText().equals("Update product") && text.equals("Save product"))) return button;
        return null;
    }
}
