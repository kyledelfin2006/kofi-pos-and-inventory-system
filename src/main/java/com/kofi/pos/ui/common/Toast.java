package com.kofi.pos.ui.common; // Shared UI helpers.

import javafx.animation.FadeTransition;   // Fade-out when a toast goes away.
import javafx.animation.PauseTransition;  // Timer before auto-dismiss.
import javafx.geometry.Insets;            // Padding.
import javafx.geometry.Pos;               // Alignment.
import javafx.scene.Node;                 // Overlay node handed to the shell.
import javafx.scene.control.Label;        // Text pieces.
import javafx.scene.layout.HBox;          // One toast.
import javafx.scene.layout.StackPane;     // Alignment inside the overlay.
import javafx.scene.layout.VBox;          // Stack of toasts.
import javafx.util.Duration;              // Time lengths.

/** Short messages shown bottom-right over the app. Success toasts fade by themselves; errors stay until clicked. */
public final class Toast {
    private static final int MAX_VISIBLE = 3; // Oldest toast is dropped beyond this.
    private final VBox stack = new VBox(8);   // Toasts, newest at the bottom.

    public Toast() {
        stack.setAlignment(Pos.BOTTOM_RIGHT);
        stack.setPadding(new Insets(0, 24, 22, 24));
        stack.setMaxSize(VBox.USE_PREF_SIZE, VBox.USE_PREF_SIZE); // Only as big as its toasts, so clicks elsewhere pass through.
        StackPane.setAlignment(stack, Pos.BOTTOM_RIGHT);
        stack.setPickOnBounds(false);
    }

    /** The node to place on top of the app layout (inside a StackPane). */
    public Node node() { return stack; }

    /** Shows a confirmation that fades after about three seconds. */
    public void success(String message) { show(message, false); }

    /** Shows a problem that stays until the user clicks it. */
    public void error(String message) { show(message, true); }

    private void show(String message, boolean error) {
        Label mark = new Label(error ? "!" : "✓");           // A symbol too, so colour is not the only cue.
        mark.getStyleClass().add("toast-mark");
        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);
        text.setMaxWidth(340);
        HBox toast = new HBox(10, mark, text);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast", error ? "toast-error" : "toast-success");
        toast.setMaxWidth(HBox.USE_PREF_SIZE);
        toast.setAccessibleText((error ? "Error: " : "") + message);
        toast.setOnMouseClicked(event -> dismiss(toast));    // Click to close.

        stack.getChildren().add(toast);
        while (stack.getChildren().size() > MAX_VISIBLE) stack.getChildren().remove(0);
        if (!error) {                                        // Errors wait for the user.
            PauseTransition wait = new PauseTransition(Duration.seconds(3));
            wait.setOnFinished(event -> dismiss(toast));
            wait.play();
        }
    }

    private void dismiss(Node toast) {
        if (!stack.getChildren().contains(toast)) return;    // Already gone.
        FadeTransition fade = new FadeTransition(Duration.millis(220), toast);
        fade.setToValue(0);
        fade.setOnFinished(event -> stack.getChildren().remove(toast));
        fade.play();
    }
}
