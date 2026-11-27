package com.kofi.pos.ui.common; // Shared UI helpers.

import javafx.scene.control.Alert;       // Base confirmation dialog.
import javafx.scene.control.Button;      // Lets us style the action buttons.
import javafx.scene.control.ButtonBar;   // Button roles.
import javafx.scene.control.ButtonType;  // Custom button labels.
import javafx.scene.control.DialogPane;  // Where the stylesheet attaches.
import javafx.stage.Window;              // Owner window.

/** Confirmation dialogs that use the app stylesheet and name their action ("Delete product", not "OK"). */
public final class Dialogs {
    private Dialogs() { } // Utility class.

    /** Attaches the shared stylesheet so a dialog matches the rest of the app. */
    public static void style(DialogPane pane) {
        var stylesheet = Dialogs.class.getResource("/style.css");
        if (stylesheet != null && !pane.getStylesheets().contains(stylesheet.toExternalForm())) {
            pane.getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    /**
     * Asks the user to confirm an action.
     *
     * @param title        short question shown as the heading
     * @param message      what will happen
     * @param confirmLabel the verb on the action button, for example "Delete product"
     * @param destructive  true makes the action button red
     * @return true when the user chose the action button; Cancel, Esc or closing the dialog returns false
     */
    public static boolean confirm(Window owner, String title, String message, String confirmLabel, boolean destructive) {
        ButtonType action = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.NONE, message, action, cancel);
        if (owner != null) alert.initOwner(owner);              // Centre over the main window.
        alert.setTitle(title);
        alert.setHeaderText(title);
        style(alert.getDialogPane());
        alert.getDialogPane().setPrefWidth(440);                // Wide enough that the message wraps instead of being cut off.
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        Button actionButton = (Button) alert.getDialogPane().lookupButton(action);
        actionButton.getStyleClass().add(destructive ? "danger-button" : "primary-button");
        Button cancelButton = (Button) alert.getDialogPane().lookupButton(cancel);
        cancelButton.getStyleClass().add("secondary-button");
        actionButton.setDefaultButton(!destructive);            // A destructive action is never the Enter default.
        cancelButton.setDefaultButton(destructive);
        return alert.showAndWait().orElse(cancel) == action;
    }
}
