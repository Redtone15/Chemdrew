package com.molview.app.ui;

import com.molview.app.model.ArrowType;
import com.molview.app.model.BondType;
import com.molview.app.model.BracketType;
import com.molview.app.model.ChargeKind;
import com.molview.app.model.ElectronKind;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Small vector icons drawn with JavaFX canvas nodes (no external assets). */
public final class Icons {
    private static final Color INK = Color.web("#334155");

    private Icons() {
    }

    public static Node bondIcon(BondType type) {
        Canvas canvas = canvas(30, 20);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(INK);
        gc.setFill(INK);
        gc.setLineWidth(2.0);
        gc.setLineCap(StrokeLineCap.ROUND);
        double y = 10;
        switch (type) {
            case SINGLE -> gc.strokeLine(4, y, 26, y);
            case DOUBLE -> {
                gc.strokeLine(4, y - 3, 26, y - 3);
                gc.strokeLine(4, y + 3, 26, y + 3);
            }
            case TRIPLE -> {
                gc.strokeLine(4, y - 5, 26, y - 5);
                gc.strokeLine(4, y, 26, y);
                gc.strokeLine(4, y + 5, 26, y + 5);
            }
            case WEDGE -> gc.fillPolygon(new double[]{5, 25, 25}, new double[]{y, y - 6, y + 6}, 3);
            case DASHED -> {
                for (int i = 0; i < 6; i++) {
                    double x = 5 + i * 3.8;
                    double half = 0.8 + i * 0.9;
                    gc.strokeLine(x, y - half, x, y + half);
                }
            }
            case WAVY -> {
                gc.beginPath();
                for (int i = 0; i <= 28; i++) {
                    double x = 4 + i * 22 / 28.0;
                    double yy = y + Math.sin(i / 28.0 * Math.PI * 5) * 3.2;
                    if (i == 0) {
                        gc.moveTo(x, yy);
                    } else {
                        gc.lineTo(x, yy);
                    }
                }
                gc.stroke();
            }
            case HYDROGEN -> {
                gc.setStroke(Color.web("#64748b"));
                gc.setLineDashes(3.5, 3);
                gc.strokeLine(4, y, 26, y);
            }
            case AROMATIC -> {
                gc.strokeLine(4, y, 26, y);
                gc.setLineDashes(3.5, 3);
                gc.setLineWidth(1.6);
                gc.strokeLine(4, y + 4, 26, y + 4);
            }
            case RESONANCE -> {
                gc.strokeLine(4, y, 26, y);
                gc.setLineDashes(1.2, 3);
                gc.setLineWidth(1.9);
                gc.strokeLine(4, y + 4, 26, y + 4);
            }
        }
        return canvas;
    }

    public static Node arrowIcon(ArrowType type) {
        Canvas canvas = canvas(32, 22);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(INK);
        gc.setFill(INK);
        gc.setLineWidth(2);
        gc.setLineCap(StrokeLineCap.ROUND);
        switch (type) {
            case STRAIGHT -> {
                gc.strokeLine(4, 11, 26, 11);
                gc.fillPolygon(new double[]{29, 21, 21}, new double[]{11, 6, 16}, 3);
            }
            case CURVED -> {
                gc.beginPath();
                gc.moveTo(4, 17);
                gc.quadraticCurveTo(15, 0, 27, 8);
                gc.stroke();
                gc.fillPolygon(new double[]{29, 20, 23}, new double[]{8, 7, 14}, 3);
            }
            case FISHHOOK -> {
                gc.beginPath();
                gc.moveTo(4, 17);
                gc.quadraticCurveTo(16, 2, 27, 8);
                gc.stroke();
                gc.strokeLine(27, 8, 20, 7);
                gc.strokeLine(27, 8, 24, 13);
            }
        }
        return canvas;
    }

    public static Node electronIcon(ElectronKind kind) {
        Canvas canvas = canvas(28, 20);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(INK);
        if (kind == ElectronKind.LONE_PAIR) {
            gc.fillOval(8, 8, 4, 4);
            gc.fillOval(16, 8, 4, 4);
        } else {
            gc.fillOval(12, 7, 5, 5);
        }
        return canvas;
    }

    public static Node chargeIcon(ChargeKind kind) {
        Label label = new Label(kind.label());
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        label.setStyle("-fx-text-fill: " + switch (kind) {
            case POSITIVE -> "#1d4ed8";
            case NEGATIVE -> "#b91c1c";
            case PARTIAL_POSITIVE -> "#b45309";
            case PARTIAL_NEGATIVE -> "#0f766e";
        } + ";");
        label.setMouseTransparent(true);
        return label;
    }

    public static Node bracketIcon(BracketType type) {
        Label label = new Label(type.open() + type.close());
        label.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16));
        label.setStyle("-fx-text-fill: #334155;");
        label.setMouseTransparent(true);
        return label;
    }

    public static Node periodicTableIcon() {
        Canvas canvas = canvas(28, 22);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        String[] colors = {"#f7b7ae", "#a9d8f5", "#ffe29a", "#c5e8b4", "#cabdf5", "#9fd8c7"};
        int i = 0;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                gc.setFill(Color.web(colors[i++ % colors.length]));
                gc.fillRoundRect(4 + col * 7, 4 + row * 7, 5.5, 5.5, 1.5, 1.5);
            }
        }
        return canvas;
    }

    public static Node eraserIcon() {
        Canvas canvas = canvas(24, 22);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web("#fecdd3"));
        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(1.6);
        gc.fillPolygon(new double[]{6, 13, 21, 14}, new double[]{15, 4, 8, 19}, 4);
        gc.strokePolygon(new double[]{6, 13, 21, 14}, new double[]{15, 4, 8, 19}, 4);
        gc.setFill(Color.web("#ffffff", 0.9));
        gc.fillPolygon(new double[]{14, 21, 19, 12}, new double[]{19, 8, 6, 17}, 4);
        gc.setStroke(Color.web("#f43f5e", 0.8));
        gc.setLineWidth(1.6);
        gc.strokeLine(5, 19, 19, 19);
        return canvas;
    }

    public static Node glyph(String glyph, double size, String color) {
        Label label = new Label(glyph);
        label.setFont(Font.font("Segoe UI Symbol", FontWeight.SEMI_BOLD, size));
        label.setStyle("-fx-text-fill: " + color + ";");
        label.setMouseTransparent(true);
        return label;
    }

    public static Label toolbarGlyph(String glyph) {
        Label label = new Label(glyph);
        label.setFont(Font.font("Segoe UI Symbol", FontWeight.NORMAL, 17));
        label.setStyle("-fx-text-fill: #334155;");
        label.setMouseTransparent(true);
        return label;
    }

    private static Canvas canvas(double width, double height) {
        Canvas canvas = new Canvas(width, height);
        canvas.setMouseTransparent(true);
        return canvas;
    }
}
