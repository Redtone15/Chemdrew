package com.molview.app;

import com.molview.app.editor.EditorController;
import com.molview.app.model.Atom;
import com.molview.app.model.TextLabel;
import com.molview.app.ui.ExportDialog;
import com.molview.app.ui.PeriodicTableDialog;
import com.molview.app.ui.SampleProjects;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens the main dialogs and captures them for visual verification. */
public final class UiSmokeTest extends Application {
    private Stage owner;
    private final Path output = Path.of("build", "test-output");

    @Override
    public void start(Stage stage) throws Exception {
        Files.createDirectories(output);
        owner = stage;
        StackPane placeholder = new StackPane();
        placeholder.setStyle("-fx-background-color: #eef2f7;");
        stage.setScene(new Scene(placeholder, 900, 600));
        stage.setTitle("UI smoke test");
        stage.show();

        PeriodicTableDialog periodic = new PeriodicTableDialog(stage, "C");
        periodic.initModality(Modality.NONE);
        periodic.show();
        PauseTransition p1 = new PauseTransition(Duration.millis(700));
        p1.setOnFinished(e -> {
            try {
                snapshot(periodic.getDialogPane().getScene(),
                        output.resolve("periodic-table.png"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            periodic.close();
            showExport();
        });
        p1.play();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void showExport() {
        EditorController controller = new EditorController();
        controller.replaceDocument(SampleProjects.sn2Mechanism(), null, true);
        Atom atom = controller.getDocument().atoms().get(0);
        atom.setSelected(true);
        TextLabel label = controller.getDocument().textLabels().get(0);
        label.setSelected(true);
        ExportDialog dialog = new ExportDialog(owner, controller);
        dialog.initModality(Modality.NONE);
        dialog.show();
        PauseTransition p2 = new PauseTransition(Duration.millis(1000));
        p2.setOnFinished(e -> {
            try {
                snapshot(dialog.getDialogPane().getScene(),
                        output.resolve("export-dialog.png"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            dialog.getDialogPane().lookupAll(".text-field").stream()
                    .filter(n -> n instanceof javafx.scene.control.TextField)
                    .map(n -> (javafx.scene.control.TextField) n)
                    .filter(f -> f.getText().startsWith("exports"))
                    .findFirst()
                    .ifPresent(f -> f.setText(output.resolve("dialog-export").toString()));
            dialog.getDialogPane().lookupAll(".combo-box").stream()
                    .filter(n -> n instanceof javafx.scene.control.ComboBox<?> combo
                            && combo.getItems().contains(com.molview.app.io.ExportFormat.PNG))
                    .findFirst()
                    .ifPresent(n -> ((javafx.scene.control.ComboBox) n)
                            .setValue(com.molview.app.io.ExportFormat.PDF));
            PauseTransition p3 = new PauseTransition(Duration.millis(900));
            p3.setOnFinished(e3 -> {
                try {
                    snapshot(dialog.getDialogPane().getScene(),
                            output.resolve("export-dialog-pdf.png"));
                    dialog.getDialogPane().getButtonTypes().stream()
                            .filter(bt -> "Export".equals(bt.getText()))
                            .findFirst()
                            .ifPresent(bt -> ((javafx.scene.control.Button)
                                    dialog.getDialogPane().lookupButton(bt)).fire());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                PauseTransition p4 = new PauseTransition(Duration.millis(800));
                p4.setOnFinished(e4 -> {
                    System.out.println("Dialog export exists="
                            + Files.exists(output.resolve("dialog-export.pdf")));
                    dialog.close();
                    owner.close();
                    Platform.exit();
                });
                p4.play();
            });
            p3.play();
        });
        p2.play();
    }

    private void snapshot(Scene scene, Path path) throws Exception {
        write(scene.snapshot(null), path);
        System.out.println("Wrote " + path.toAbsolutePath());
    }

    private void write(WritableImage image, Path path) throws Exception {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(buffered, "png", path.toFile());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
