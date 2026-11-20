package com.molview.app;

import com.molview.app.ui.MainWindow;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.List;

/** Application entry point. */
public final class MolViewApp extends Application {
    @Override
    public void start(Stage stage) {
        MainWindow window = new MainWindow(stage);
        window.show();

        List<String> args = getParameters().getRaw();
        String openPath = option(args, "--open");
        String screenshot = option(args, "--screenshot");
        boolean example = args.contains("--example");
        boolean blank = args.contains("--blank");
        boolean hexDemo = args.contains("--hex-demo");

        if (openPath != null) {
            Platform.runLater(() -> {
                try {
                    window.openPathForDemo(Path.of(openPath));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        } else if (hexDemo) {
            Platform.runLater(window::loadHexChainDemoForDemo);
        } else if (!blank && (example || screenshot != null)) {
            Platform.runLater(window::loadExampleForDemo);
        }

        if (screenshot != null) {
            PauseTransition delay = new PauseTransition(Duration.millis(1500));
            delay.setOnFinished(event -> {
                try {
                    window.writeScreenshot(Path.of(screenshot));
                    System.out.println("Screenshot written to " + Path.of(screenshot).toAbsolutePath());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                Platform.exit();
            });
            delay.play();
        }
    }

    private static String option(List<String> args, String name) {
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals(name) && i + 1 < args.size()) {
                return args.get(i + 1);
            }
            if (arg.startsWith(name + "=")) {
                return arg.substring(name.length() + 1);
            }
        }
        return null;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
