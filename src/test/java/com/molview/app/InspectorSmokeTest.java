package com.molview.app;

import com.molview.app.editor.EditorController;
import com.molview.app.ui.PropertiesPanel;
import com.molview.app.ui.SampleProjects;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Renders the inspector with an atom selected for visual verification. */
public final class InspectorSmokeTest extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        EditorController controller = new EditorController();
        controller.replaceDocument(SampleProjects.sn2Mechanism(), null, true);
        controller.selectOnly(controller.getDocument().atoms().get(2));
        PropertiesPanel panel = new PropertiesPanel(controller, () -> stage,
                message -> {
                }, atom -> {
        });
        BorderPane root = new BorderPane(panel);
        root.setStyle("-fx-background-color: #eef2f7;");
        Scene scene = new Scene(root, 330, 800);
        scene.getStylesheets().add(InspectorSmokeTest.class
                .getResource("/styles/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Inspector smoke test");
        stage.show();
        PauseTransition pause = new PauseTransition(Duration.millis(600));
        pause.setOnFinished(event -> {
            try {
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.WHITE);
                WritableImage image = root.snapshot(params, null);
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                int[] pixels = new int[width * height];
                image.getPixelReader().getPixels(0, 0, width, height,
                        PixelFormat.getIntArgbInstance(), pixels, 0, width);
                BufferedImage buffered = new BufferedImage(width, height,
                        BufferedImage.TYPE_INT_ARGB);
                buffered.setRGB(0, 0, width, height, pixels, 0, width);
                Path output = Path.of("build", "test-output", "inspector.png");
                Files.createDirectories(output.getParent());
                ImageIO.write(buffered, "png", output.toFile());
                System.out.println("Wrote " + output.toAbsolutePath());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            stage.close();
            Platform.exit();
        });
        pause.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
