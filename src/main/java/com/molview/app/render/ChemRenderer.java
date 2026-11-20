package com.molview.app.render;

import com.molview.app.model.Arrow;
import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.BondType;
import com.molview.app.model.Bounds;
import com.molview.app.model.Bracket;
import com.molview.app.model.BracketType;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;
import com.molview.app.model.ElectronMark;
import com.molview.app.model.Geometry;
import com.molview.app.model.HexGrid;
import com.molview.app.model.ImplicitHydrogens;
import com.molview.app.model.PeriodicTable;
import com.molview.app.model.TextLabel;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the chemical document in world coordinates. The caller establishes the
 * canvas transform, which means the same code serves screen display and
 * high-resolution raster export.
 */
public final class ChemRenderer {
    public static final String INK = "#263244";
    public static final String ACCENT = "#2563eb";
    public static final String MUTED = "#64748b";
    public static final String DANGER = "#b91c1c";
    public static final String BOND_COLOR = "#2f3b4c";

    public static final Font ATOM_FONT = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 18);
    public static final Font ATOM_FONT_SMALL = Font.font("Segoe UI", FontWeight.BOLD, 13);
    public static final Font CHARGE_FONT = Font.font("Segoe UI", FontWeight.BOLD, 16);
    public static final Font BRACKET_LABEL_FONT = Font.font("Segoe UI", FontWeight.SEMI_BOLD, 16);
    public static final Font UI_SMALL_FONT = Font.font("Segoe UI", FontWeight.MEDIUM, 12);

    private static final Map<String, Double> WIDTH_CACHE = new ConcurrentHashMap<>();

    private ChemRenderer() {
    }

    public static void drawScene(GraphicsContext gc, ChemDocument document, RenderSettings settings) {
        for (Bond bond : document.bonds()) {
            drawBond(gc, bond, document, settings);
        }
        for (Arrow arrow : document.arrows()) {
            drawArrow(gc, arrow, settings);
        }
        for (Bracket bracket : document.brackets()) {
            drawBracket(gc, bracket, settings);
        }
        for (ElectronMark mark : document.electronMarks()) {
            drawElectronMark(gc, mark, document, settings);
        }
        for (Atom atom : document.atoms()) {
            drawAtom(gc, atom, document, settings);
        }
        for (ChargeMark charge : document.chargeMarks()) {
            drawCharge(gc, charge, settings);
        }
        for (TextLabel label : document.textLabels()) {
            drawTextLabel(gc, label, settings);
        }
        if (settings.showSelection()) {
            drawSelectionOverlay(gc, document, settings);
        }
    }

    public static boolean isLabelVisible(Atom atom, ChemDocument document, boolean showCarbonLabels) {
        if (atom.isGeneric()) {
            return true;
        }
        if (atom.isCarbon()) {
            return showCarbonLabels || document.bondCount(atom) == 0;
        }
        return true;
    }

    public static double atomLabelRadius(Atom atom, ChemDocument document, boolean showCarbonLabels) {
        if (!isLabelVisible(atom, document, showCarbonLabels)) {
            return 0;
        }
        double[] bounds = labelXBounds(atom, document);
        double reach = Math.max(Math.abs(bounds[0]), Math.abs(bounds[1]));
        return Math.min(34, Math.max(9.5, reach + 3.5));
    }

    /**
     * Distance from the atom centre to the visible label boundary along a bond
     * direction. Because the element symbol is anchored at the atom and the H
     * formula extends away from the bond, a bond on the element side is trimmed
     * at the element glyph rather than at the hydrogen side.
     */
    public static double labelBoundaryDistance(Atom atom, ChemDocument document,
                                               boolean showCarbonLabels, double angle) {
        if (!isLabelVisible(atom, document, showCarbonLabels)) {
            return 0;
        }
        double[] bounds = labelXBounds(atom, document);
        double halfHeight = ATOM_FONT.getSize() / 2 + 1.5;
        double dx = Math.cos(angle);
        double dy = Math.sin(angle);
        double padding = 3.5;
        double tx = Double.POSITIVE_INFINITY;
        double ty = Double.POSITIVE_INFINITY;
        if (dx > 1e-6) {
            tx = (bounds[1] + padding) / dx;
        } else if (dx < -1e-6) {
            tx = (bounds[0] - padding) / dx;
        }
        if (dy > 1e-6) {
            ty = (halfHeight + padding) / dy;
        } else if (dy < -1e-6) {
            ty = (-halfHeight - padding) / dy;
        }
        double distance = Math.min(tx, ty);
        return Double.isFinite(distance) && distance > 0 ? distance : 0;
    }

    private static double[] labelXBounds(Atom atom, ChemDocument document) {
        String label = ImplicitHydrogens.label(atom, document);
        double total = measureText(label, ATOM_FONT);
        if (atom.isGeneric() || "H".equals(atom.getElementSymbol())) {
            return new double[]{-total / 2, total / 2};
        }
        ImplicitHydrogens.Orientation orientation = ImplicitHydrogens.orientation(atom, document);
        if (orientation == ImplicitHydrogens.Orientation.CENTER) {
            return new double[]{-total / 2, total / 2};
        }
        double element = measureText(atom.getElementSymbol(), ATOM_FONT);
        if (orientation == ImplicitHydrogens.Orientation.PREFIX) {
            return new double[]{element / 2 - total, element / 2};
        }
        return new double[]{-element / 2, total - element / 2};
    }

    private static double measureText(String text, Font font) {
        String key = font.getName() + "|" + font.getSize() + "|" + text;
        return WIDTH_CACHE.computeIfAbsent(key, ignored -> {
            javafx.scene.text.Text measurement = new javafx.scene.text.Text(text);
            measurement.setFont(font);
            return measurement.getLayoutBounds().getWidth();
        });
    }

    private static void drawAtom(GraphicsContext gc, Atom atom, ChemDocument document, RenderSettings settings) {
        String label = ImplicitHydrogens.label(atom, document);
        boolean visible = isLabelVisible(atom, document, settings.showCarbonLabels());
        double radius = visible ? atomLabelRadius(atom, document, settings.showCarbonLabels()) : 8;

        if (settings.showSelection() && atom.isSelected()) {
            gc.setFill(Color.web(ACCENT, 0.12));
            gc.fillOval(atom.getX() - radius - 5, atom.getY() - radius - 5,
                    (radius + 5) * 2, (radius + 5) * 2);
            gc.setStroke(Color.web(ACCENT, 0.9));
            gc.setLineWidth(1.5 / settings.zoom());
            gc.strokeOval(atom.getX() - radius - 4, atom.getY() - radius - 4,
                    (radius + 4) * 2, (radius + 4) * 2);
        }
        if (!visible) {
            return;
        }
        String color = atom.isGeneric()
                ? (atom.getElementSymbol().equals("R") ? "#7c3aed" : "#0e7490")
                : PeriodicTable.atomColor(atom.getElementSymbol());
        gc.setFont(ATOM_FONT);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(Color.web(color));
        double textX = atom.getX();
        ImplicitHydrogens.Orientation orientation = ImplicitHydrogens.orientation(atom, document);
        if (atom.isGeneric() || "H".equals(atom.getElementSymbol())
                || orientation == ImplicitHydrogens.Orientation.CENTER) {
            gc.setTextAlign(TextAlignment.CENTER);
        } else {
            double element = measureText(atom.getElementSymbol(), ATOM_FONT);
            if (orientation == ImplicitHydrogens.Orientation.PREFIX) {
                gc.setTextAlign(TextAlignment.RIGHT);
                textX = atom.getX() + element / 2;
            } else {
                gc.setTextAlign(TextAlignment.LEFT);
                textX = atom.getX() - element / 2;
            }
        }
        gc.fillText(label, textX, atom.getY() + 0.5);
    }

    private static void drawBond(GraphicsContext gc, Bond bond, ChemDocument document,
                                 RenderSettings settings) {
        Atom from = bond.getFrom();
        Atom to = bond.getTo();
        double x1 = from.getX();
        double y1 = from.getY();
        double x2 = to.getX();
        double y2 = to.getY();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (length < 0.001) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double bondAngle = Math.atan2(dy, dx);
        double pad1 = labelBoundaryDistance(from, document,
                settings.showCarbonLabels(), bondAngle);
        double pad2 = labelBoundaryDistance(to, document,
                settings.showCarbonLabels(), bondAngle + Math.PI);
        if (pad1 + pad2 + 4 > length) {
            double scale = Math.max(0, length - 4) / (pad1 + pad2);
            pad1 *= scale;
            pad2 *= scale;
        }
        double sx = x1 + ux * pad1;
        double sy = y1 + uy * pad1;
        double ex = x2 - ux * pad2;
        double ey = y2 - uy * pad2;
        double trimmedLength = Math.hypot(ex - sx, ey - sy);
        if (trimmedLength < 1.0) {
            return;
        }

        boolean selected = settings.showSelection() && bond.isSelected();
        Color color = selected ? Color.web(ACCENT) : Color.web(BOND_COLOR);
        double width = selected ? 2.8 : 2.25;

        if (selected) {
            gc.save();
            gc.setStroke(Color.web(ACCENT, 0.16));
            gc.setLineWidth(10);
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.strokeLine(sx, sy, ex, ey);
            gc.restore();
        }

        gc.save();
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(width);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);

        switch (bond.getType()) {
            case SINGLE -> gc.strokeLine(sx, sy, ex, ey);
            case DOUBLE -> drawDouble(gc, bond, document, sx, sy, ex, ey);
            case TRIPLE -> drawTriple(gc, sx, sy, ux, uy, trimmedLength);
            case WEDGE -> drawWedge(gc, sx, sy, ux, uy, trimmedLength, false);
            case DASHED -> drawWedge(gc, sx, sy, ux, uy, trimmedLength, true);
            case WAVY -> drawWavy(gc, sx, sy, ux, uy, trimmedLength);
            case HYDROGEN -> {
                gc.setStroke(Color.web("#64748b"));
                gc.setLineWidth(2);
                gc.setLineCap(StrokeLineCap.ROUND);
                for (double[] dash : BondGeometry.horizontalDashes(sx, sy, ex, ey)) {
                    gc.strokeLine(dash[0], dash[1], dash[2], dash[3]);
                }
            }
            case AROMATIC -> {
                gc.strokeLine(sx, sy, ex, ey);
                gc.save();
                gc.setLineDashes(4.5, 4);
                gc.setLineWidth(1.9);
                gc.setStroke(Color.web(BOND_COLOR, 0.8));
                double ox = -uy * 3.4;
                double oy = ux * 3.4;
                gc.strokeLine(sx + ox, sy + oy, ex + ox, ey + oy);
                gc.restore();
            }
            case RESONANCE -> {
                gc.strokeLine(sx, sy, ex, ey);
                gc.save();
                gc.setLineDashes(1.5, 4);
                gc.setLineWidth(2.2);
                double ox = -uy * 3.4;
                double oy = ux * 3.4;
                gc.strokeLine(sx + ox, sy + oy, ex + ox, ey + oy);
                gc.restore();
            }
        }
        gc.restore();
    }

    private static void drawDouble(GraphicsContext gc, Bond bond, ChemDocument document,
                                   double sx, double sy, double ex, double ey) {
        gc.strokeLine(sx, sy, ex, ey);
        double[] secondary = BondGeometry.doubleSecondaryLine(
                document, bond, sx, sy, ex, ey);
        if (secondary != null) {
            gc.strokeLine(secondary[0], secondary[1], secondary[2], secondary[3]);
        }
        for (double[] connector : BondGeometry.doubleJunctionConnectors(
                document, bond, sx, sy, ex, ey)) {
            gc.strokeLine(connector[0], connector[1], connector[2], connector[3]);
        }
    }

    private static void drawParallel(GraphicsContext gc, double sx, double sy,
                                     double ux, double uy, double length, double offset,
                                     boolean dotted) {
        double px = -uy * offset;
        double py = ux * offset;
        gc.strokeLine(sx + px, sy + py, sx + ux * length + px, sy + uy * length + py);
        gc.strokeLine(sx - px, sy - py, sx + ux * length - px, sy + uy * length - py);
    }

    private static void drawTriple(GraphicsContext gc, double sx, double sy,
                                   double ux, double uy, double length) {
        gc.strokeLine(sx, sy, sx + ux * length, sy + uy * length);
        drawParallel(gc, sx, sy, ux, uy, length, 4.0, false);
    }

    private static void drawWedge(GraphicsContext gc, double sx, double sy,
                                  double ux, double uy, double length, boolean dashed) {
        double px = -uy;
        double py = ux;
        if (dashed) {
            int segments = Math.max(5, (int) (length / 8));
            for (int i = 0; i < segments; i++) {
                double t = (i + 0.6) / segments;
                double cx = sx + ux * length * t;
                double cy = sy + uy * length * t;
                double half = 1.0 + 4.8 * t;
                gc.setLineWidth(1.8);
                gc.strokeLine(cx - px * half, cy - py * half, cx + px * half, cy + py * half);
            }
        } else {
            double halfEnd = Math.min(7.5, Math.max(4.0, length * 0.12));
            double[] xs = {sx, sx + ux * length + px * halfEnd, sx + ux * length - px * halfEnd};
            double[] ys = {sy, sy + uy * length + py * halfEnd, sy + uy * length - py * halfEnd};
            gc.fillPolygon(xs, ys, 3);
            gc.setLineWidth(1.4);
            gc.strokeLine(sx, sy, sx + ux * length, sy + uy * length);
        }
    }

    private static void drawWavy(GraphicsContext gc, double sx, double sy,
                                 double ux, double uy, double length) {
        double px = -uy;
        double py = ux;
        int samples = Math.max(24, (int) length / 3);
        double[] xs = new double[samples + 1];
        double[] ys = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double wave = Math.sin(t * Math.PI * 10) * 2.9;
            xs[i] = sx + ux * length * t + px * wave;
            ys[i] = sy + uy * length * t + py * wave;
        }
        gc.strokePolyline(xs, ys, xs.length);
    }

    private static void drawCharge(GraphicsContext gc, ChargeMark charge, RenderSettings settings) {
        String color = switch (charge.getKind()) {
            case POSITIVE -> "#1d4ed8";
            case NEGATIVE -> DANGER;
            case PARTIAL_POSITIVE -> "#b45309";
            case PARTIAL_NEGATIVE -> "#0f766e";
        };
        if (settings.showSelection() && charge.isSelected()) {
            gc.setFill(Color.web(ACCENT, 0.13));
            gc.setStroke(Color.web(ACCENT, 0.8));
            gc.setLineWidth(1.2 / settings.zoom());
            double w = charge.getKind().isPartial() ? 30 : 20;
            gc.fillRoundRect(charge.getX() - w / 2 - 2, charge.getY() - 11, w + 4, 22, 6, 6);
            gc.strokeRoundRect(charge.getX() - w / 2 - 2, charge.getY() - 11, w + 4, 22, 6, 6);
        }
        gc.setFont(CHARGE_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(Color.web(color));
        gc.fillText(charge.getKind().label(), charge.getX(), charge.getY());
    }

    private static void drawElectronMark(GraphicsContext gc, ElectronMark mark,
                                         ChemDocument document, RenderSettings settings) {
        boolean selected = settings.showSelection() && mark.isSelected();
        double[] offset = lonePairDotOffset(mark, document);
        if (selected) {
            double r = 2.35;
            double halfX = Math.abs(offset[0]) + r + 5;
            double halfY = Math.abs(offset[1]) + r + 5;
            gc.setFill(Color.web(ACCENT, 0.13));
            gc.setStroke(Color.web(ACCENT, 0.8));
            gc.setLineWidth(1.2 / settings.zoom());
            gc.fillRoundRect(mark.getX() - halfX, mark.getY() - halfY,
                    halfX * 2, halfY * 2, 6, 6);
            gc.strokeRoundRect(mark.getX() - halfX, mark.getY() - halfY,
                    halfX * 2, halfY * 2, 6, 6);
        }
        gc.setFill(Color.web(selected ? ACCENT : "#1f2937"));
        double r = 2.35;
        if (mark.getKind() == com.molview.app.model.ElectronKind.LONE_PAIR) {
            gc.fillOval(mark.getX() + offset[0] - r, mark.getY() + offset[1] - r,
                    r * 2, r * 2);
            gc.fillOval(mark.getX() - offset[0] - r, mark.getY() - offset[1] - r,
                    r * 2, r * 2);
        } else {
            gc.fillOval(mark.getX() - 2.8, mark.getY() - 2.8, 5.6, 5.6);
        }
    }

    /**
     * The two dots of a lone pair are drawn tangentially around their host atom,
     * so a pair at the side is vertical and a pair in an angular gap lies between
     * the adjacent bonds.
     */
    public static double[] lonePairDotOffset(ElectronMark mark, ChemDocument document) {
        if (mark.getKind() != com.molview.app.model.ElectronKind.LONE_PAIR
                || mark.getAttachedAtomId() == null || document == null) {
            return new double[]{5.4, 0};
        }
        for (Atom atom : document.atoms()) {
            if (atom.getId().equals(mark.getAttachedAtomId())) {
                double dx = mark.getX() - atom.getX();
                double dy = mark.getY() - atom.getY();
                if (Math.hypot(dx, dy) < 0.1) {
                    return new double[]{5.4, 0};
                }
                double tangent = Math.atan2(dy, dx) + Math.PI / 2;
                return new double[]{Math.cos(tangent) * 5.4, Math.sin(tangent) * 5.4};
            }
        }
        return new double[]{5.4, 0};
    }

    private static void drawTextLabel(GraphicsContext gc, TextLabel label, RenderSettings settings) {
        Font font = Font.font("Segoe UI",
                label.isBold() ? FontWeight.BOLD : FontWeight.NORMAL,
                FontPosture.REGULAR, label.getFontSize());
        if (settings.showSelection() && label.isSelected()) {
            double w = Math.max(18, label.estimatedWidth());
            double h = Math.max(18, label.estimatedHeight());
            gc.setFill(Color.web(ACCENT, 0.1));
            gc.setStroke(Color.web(ACCENT, 0.7));
            gc.setLineWidth(1.2 / settings.zoom());
            gc.fillRoundRect(label.getX() - w / 2 - 4, label.getY() - h / 2 - 3,
                    w + 8, h + 6, 6, 6);
            gc.strokeRoundRect(label.getX() - w / 2 - 4, label.getY() - h / 2 - 3,
                    w + 8, h + 6, 6, 6);
        }
        gc.setFont(font);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(Color.web(label.getColor()));
        gc.fillText(label.getText(), label.getX(), label.getY());
    }

    private static void drawArrow(GraphicsContext gc, Arrow arrow, RenderSettings settings) {
        double[][] points = arrow.curvedPoints();
        boolean selected = settings.showSelection() && arrow.isSelected();
        Color color = Color.web(selected ? ACCENT : INK);
        if (selected) {
            gc.save();
            gc.setStroke(Color.web(ACCENT, 0.13));
            gc.setLineWidth(11);
            gc.setLineCap(StrokeLineCap.ROUND);
            strokePolyline(gc, points);
            gc.restore();
        }
        gc.save();
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(selected ? 2.8 : 2.2);
        gc.setLineCap(StrokeLineCap.ROUND);
        strokePolyline(gc, points);
        drawArrowHead(gc, points[points.length - 2], points[points.length - 1],
                arrow.getType().hasFullHead(), 13);
        gc.restore();
    }

    private static void strokePolyline(GraphicsContext gc, double[][] points) {
        gc.beginPath();
        gc.moveTo(points[0][0], points[0][1]);
        for (int i = 1; i < points.length; i++) {
            gc.lineTo(points[i][0], points[i][1]);
        }
        gc.stroke();
    }

    private static void drawArrowHead(GraphicsContext gc, double[] previous, double[] tip,
                                      boolean full, double length) {
        double angle = Math.atan2(tip[1] - previous[1], tip[0] - previous[0]);
        if (full) {
            double half = 5.6;
            double backX = tip[0] - Math.cos(angle) * length;
            double backY = tip[1] - Math.sin(angle) * length;
            double px = -Math.sin(angle) * half;
            double py = Math.cos(angle) * half;
            gc.fillPolygon(
                    new double[]{tip[0], backX + px, backX - px},
                    new double[]{tip[1], backY + py, backY - py}, 3);
        } else {
            double backAngle = angle + Math.PI - 0.62;
            gc.setLineWidth(2.4);
            gc.strokeLine(tip[0], tip[1],
                    tip[0] + Math.cos(backAngle) * 13, tip[1] + Math.sin(backAngle) * 13);
            double other = angle + Math.PI + 0.62;
            gc.setLineWidth(1.5);
            gc.strokeLine(tip[0], tip[1],
                    tip[0] + Math.cos(other) * 5.5, tip[1] + Math.sin(other) * 5.5);
        }
    }

    private static void drawBracket(GraphicsContext gc, Bracket bracket, RenderSettings settings) {
        boolean selected = settings.showSelection() && bracket.isSelected();
        Color color = Color.web(selected ? ACCENT : INK);
        double x = bracket.getX();
        double y = bracket.getY();
        double w = bracket.getWidth();
        double h = bracket.getHeight();
        gc.save();
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(selected ? 2.7 : 2.1);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        if (selected) {
            gc.setStroke(Color.web(ACCENT, 0.25));
            gc.setLineWidth(7);
            drawBracketPath(gc, bracket.getType(), x, y, w, h);
            gc.setStroke(color);
            gc.setLineWidth(2.4);
        }
        drawBracketPath(gc, bracket.getType(), x, y, w, h);
        if (!bracket.getLabel().isBlank()) {
            gc.setFont(BRACKET_LABEL_FONT);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.CENTER);
            gc.setFill(Color.web(selected ? ACCENT : "#334155"));
            gc.fillText(bracket.getLabel(), bracket.labelX(), bracket.labelY());
        }
        gc.restore();
    }

    private static void drawBracketPath(GraphicsContext gc, BracketType type,
                                        double x, double y, double w, double h) {
        gc.beginPath();
        switch (type) {
            case SQUARE -> {
                double tick = Math.min(16, w * 0.12);
                gc.moveTo(x + tick, y);
                gc.lineTo(x, y);
                gc.lineTo(x, y + h);
                gc.lineTo(x + tick, y + h);
                gc.moveTo(x + w - tick, y);
                gc.lineTo(x + w, y);
                gc.lineTo(x + w, y + h);
                gc.lineTo(x + w - tick, y + h);
            }
            case ROUND -> {
                gc.moveTo(x + Math.min(18, w * 0.15), y - 1);
                gc.quadraticCurveTo(x - 5, y + h * 0.25, x + 4, y + h * 0.5);
                gc.quadraticCurveTo(x - 5, y + h * 0.75, x + Math.min(18, w * 0.15), y + h + 1);
                gc.moveTo(x + w - Math.min(18, w * 0.15), y - 1);
                gc.quadraticCurveTo(x + w + 5, y + h * 0.25, x + w - 4, y + h * 0.5);
                gc.quadraticCurveTo(x + w + 5, y + h * 0.75, x + w - Math.min(18, w * 0.15), y + h + 1);
            }
            case CURLY -> {
                gc.moveTo(x + w * 0.2, y);
                gc.quadraticCurveTo(x + w * 0.05, y, x + w * 0.07, y + h * 0.28);
                gc.quadraticCurveTo(x + w * 0.08, y + h * 0.44, x + w * 0.01, y + h * 0.5);
                gc.quadraticCurveTo(x + w * 0.08, y + h * 0.56, x + w * 0.07, y + h * 0.72);
                gc.quadraticCurveTo(x + w * 0.05, y + h, x + w * 0.2, y + h);

                gc.moveTo(x + w * 0.8, y);
                gc.quadraticCurveTo(x + w * 0.95, y, x + w * 0.93, y + h * 0.28);
                gc.quadraticCurveTo(x + w * 0.92, y + h * 0.44, x + w * 0.99, y + h * 0.5);
                gc.quadraticCurveTo(x + w * 0.92, y + h * 0.56, x + w * 0.93, y + h * 0.72);
                gc.quadraticCurveTo(x + w * 0.95, y + h, x + w * 0.8, y + h);
            }
        }
        gc.stroke();
    }

    private static void drawSelectionOverlay(GraphicsContext gc, ChemDocument document,
                                             RenderSettings settings) {
        List<SelectionHandles.Handle> handles = SelectionHandles.forDocument(document);
        double zoom = Math.max(0.1, settings.zoom());
        double handleRadius = 4.5 / zoom;
        gc.save();
        gc.setLineWidth(1.15 / zoom);
        gc.setStroke(Color.web(ACCENT, 0.75));
        gc.setFill(Color.WHITE);
        for (SelectionHandles.Handle handle : handles) {
            if (handle.kind() == SelectionHandles.Kind.ARROW_CONTROL) {
                gc.fillRect(handle.x() - handleRadius, handle.y() - handleRadius,
                        handleRadius * 2, handleRadius * 2);
                gc.strokeRect(handle.x() - handleRadius, handle.y() - handleRadius,
                        handleRadius * 2, handleRadius * 2);
            } else if (handle.target() instanceof Arrow) {
                gc.fillOval(handle.x() - handleRadius, handle.y() - handleRadius,
                        handleRadius * 2, handleRadius * 2);
                gc.strokeOval(handle.x() - handleRadius, handle.y() - handleRadius,
                        handleRadius * 2, handleRadius * 2);
            } else {
                gc.fillRect(handle.x() - handleRadius, handle.y() - handleRadius,
                        handleRadius * 2, handleRadius * 2);
                gc.strokeRect(handle.x() - handleRadius, handle.y() - handleRadius,
                        handleRadius * 2, handleRadius * 2);
            }
        }

        if (document.selectedObjects().size() > 1) {
            com.molview.app.model.Bounds bounds = document.selectionBounds();
            if (bounds != null) {
                gc.setStroke(Color.web(ACCENT, 0.55));
                gc.setLineDashes(5 / zoom, 4 / zoom);
                gc.strokeRect(bounds.minX - 8, bounds.minY - 8,
                        bounds.width() + 16, bounds.height() + 16);
            }
        }
        gc.restore();
    }

    /**
     * Draws the pointy-top honeycomb lattice in world coordinates. Atoms snap to
     * these vertices and every bond is drawn along exactly one of these edges.
     */
    public static void drawHexGrid(GraphicsContext gc, double visibleMinX, double visibleMinY,
                                   double visibleMaxX, double visibleMaxY, double zoom) {
        Bounds view = new Bounds(visibleMinX, visibleMinY, visibleMaxX, visibleMaxY);
        HexGrid.Center[] centers = HexGrid.centersIn(view);
        gc.save();
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);

        // Main honeycomb outline.
        gc.setStroke(Color.web("#d2dfee"));
        gc.setLineWidth(Math.max(0.5, 1.0 / zoom));
        for (HexGrid.Center center : centers) {
            HexGrid.Vertex[] vertices = HexGrid.verticesOfCenter(center.x(), center.y());
            gc.beginPath();
            gc.moveTo(vertices[0].x(), vertices[0].y());
            for (int i = 1; i < vertices.length; i++) {
                gc.lineTo(vertices[i].x(), vertices[i].y());
            }
            gc.closePath();
            gc.stroke();
        }

        // Six equilateral triangles per cell, drawn from the cell centre.
        gc.setStroke(Color.web("#dfe9f4"));
        gc.setLineWidth(Math.max(0.35, 0.8 / zoom));
        for (HexGrid.Center center : centers) {
            HexGrid.Vertex[] vertices = HexGrid.verticesOfCenter(center.x(), center.y());
            gc.beginPath();
            for (HexGrid.Vertex vertex : vertices) {
                gc.moveTo(center.x(), center.y());
                gc.lineTo(vertex.x(), vertex.y());
            }
            gc.stroke();
        }

        // Subdivide every one of those triangles into four equal triangles by
        // joining the midpoints of its three sides.
        gc.setStroke(Color.web("#edf3fa"));
        gc.setLineWidth(Math.max(0.25, 0.65 / zoom));
        for (HexGrid.Center center : centers) {
            HexGrid.Vertex[] vertices = HexGrid.verticesOfCenter(center.x(), center.y());
            gc.beginPath();
            for (int i = 0; i < 6; i++) {
                HexGrid.Vertex a = vertices[i];
                HexGrid.Vertex b = vertices[(i + 1) % 6];
                double caX = (center.x() + a.x()) / 2;
                double caY = (center.y() + a.y()) / 2;
                double cbX = (center.x() + b.x()) / 2;
                double cbY = (center.y() + b.y()) / 2;
                double abX = (a.x() + b.x()) / 2;
                double abY = (a.y() + b.y()) / 2;
                gc.moveTo(caX, caY);
                gc.lineTo(cbX, cbY);
                gc.lineTo(abX, abY);
                gc.closePath();
            }
            gc.stroke();
        }
        gc.restore();
    }

    public static double distanceToBond(Bond bond, double x, double y) {
        return Geometry.distanceToSegment(x, y, bond.getFrom().getX(), bond.getFrom().getY(),
                bond.getTo().getX(), bond.getTo().getY());
    }
}
