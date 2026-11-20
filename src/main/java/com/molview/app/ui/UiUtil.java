package com.molview.app.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import java.util.Optional;

/** Shared dialog styling and small UI helpers. */
public final class UiUtil {
    private UiUtil() {
    }

    public static void styleDialog(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        String css = UiUtil.class.getResource("/styles/app.css").toExternalForm();
        if (!pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
        pane.getStyleClass().add("molview-dialog");
    }

    public static void showError(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    public static void showInfo(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        styleDialog(alert);
        alert.showAndWait();
    }

    public static boolean confirm(Window owner, String title, String header, String message,
                                  String okText, String cancelText) {
        ButtonType ok = new ButtonType(okText, javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(cancelText,
                javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ok, cancel);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        styleDialog(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ok;
    }
}
