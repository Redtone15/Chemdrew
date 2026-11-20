package com.molview.app.render;

import com.molview.app.model.Arrow;
import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.Bracket;
import com.molview.app.model.Bounds;
import com.molview.app.model.ChargeMark;
import com.molview.app.model.ChemDocument;
import com.molview.app.model.ChemObject;
import com.molview.app.model.ElectronKind;
import com.molview.app.model.ElectronMark;
import com.molview.app.model.ImplicitHydrogens;
import com.molview.app.model.PeriodicTable;
import com.molview.app.model.TextLabel;

import java.util.Locale;

/** Dependency-free scalable vector graphics exporter. */
public final class SvgExporter {
    private static final String SANS = "Segoe UI, Arial, Helvetica, sans-serif";

    private SvgExporter() {
    }

    public static String render(ChemDocument document, boolean showCarbonLabels,
                                int width, int height, String backgroundColor) {
        if (document.isEmpty()) {
            throw new IllegalArgumentException("There is nothing to export.");
        }
        Bounds bounds = document.bounds();
        if (bounds == null) {
            bounds = new Bounds(0, 0, width, height);
        }
        double contentWidth = Math.max(80, bounds.width());
        double contentHeight = Math.max(80, bounds.height());
        double padding = Math.min(width, height) * 0.055;
        double availableW = Math.max(10, width - padding * 2);
        double availableH = Math.max(10, height - padding * 2);
        double scale = Math.min(availableW / contentWidth, availableH / contentHeight);
        double offsetX = (width - contentWidth * scale) / 2 - bounds.minX * scale;
        double offsetY = (height - contentHeight * scale) / 2 - bounds.minY * scale;

        StringBuilder svg = new StringBuilder(8192);
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n",
                width, height, width, height));
        svg.append("  <title>").append(xml(document.getTitle())).append("</title>\n");
        svg.append("  <desc>Chemical structure created with MolView Studio</desc>\n");
        if (backgroundColor != null) {
            svg.append("  <rect width=\"100%\" height=\"100%\" fill=\"")
                    .append(backgroundColor).append("\"/>\n");
        }
        svg.append(String.format(Locale.ROOT,
                "  <g transform=\"translate(%.3f,%.3f) scale(%.5f)\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n",
                offsetX, offsetY, scale));

        for (Bond bond : document.bonds()) {
            appendBond(svg, bond, document, showCarbonLabels);
        }
        for (Arrow arrow : document.arrows()) {
            appendArrow(svg, arrow);
        }
        for (Bracket bracket : document.brackets()) {
            appendBracket(svg, bracket);
        }
        for (ElectronMark electron : document.electronMarks()) {
            appendElectron(svg, electron, document);
        }
        for (Atom atom : document.atoms()) {
            appendAtom(svg, atom, document, showCarbonLabels);
        }
        for (ChargeMark charge : document.chargeMarks()) {
            appendCharge(svg, charge);
        }
        for (TextLabel label : document.textLabels()) {
            appendTextLabel(svg, label);
        }
        svg.append("  </g>\n</svg>\n");
        return svg.toString();
    }

    private static void appendAtom(StringBuilder svg, Atom atom, ChemDocument document,
                                   boolean showCarbonLabels) {
        if (!ChemRenderer.isLabelVisible(atom, document, showCarbonLabels)) {
            return;
        }
        String color = atom.isGeneric()
                ? (atom.getElementSymbol().equals("R") ? "#7c3aed" : "#0e7490")
                : PeriodicTable.atomColor(atom.getElementSymbol());
        String label = ImplicitHydrogens.label(atom, document);
        String anchor = "middle";
        double textX = atom.getX();
        ImplicitHydrogens.Orientation orientation = ImplicitHydrogens.orientation(atom, document);
        if (!atom.isGeneric() && !"H".equals(atom.getElementSymbol())
                && orientation == ImplicitHydrogens.Orientation.PREFIX) {
            anchor = "end";
            textX = atom.getX() + elementWidth(atom) / 2;
        } else if (!atom.isGeneric() && !"H".equals(atom.getElementSymbol())
                && orientation == ImplicitHydrogens.Orientation.SUFFIX) {
            anchor = "start";
            textX = atom.getX() - elementWidth(atom) / 2;
        }
        svg.append(String.format(Locale.ROOT,
                "    <text x=\"%.2f\" y=\"%.2f\" font-family=\"%s\" font-size=\"18\" font-weight=\"600\" " +
                        "fill=\"%s\" text-anchor=\"%s\" dominant-baseline=\"central\">%s</text>\n",
                textX, atom.getY() + 0.5, SANS, color, anchor, xml(label)));
    }

    private static void appendBond(StringBuilder svg, Bond bond, ChemDocument document,
                                   boolean showCarbonLabels) {
        Atom from = bond.getFrom();
        Atom to = bond.getTo();
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double length = Math.hypot(dx, dy);
        if (length < 0.001) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double angle = Math.atan2(dy, dx);
        double pad1 = labelBoundaryDistance(from, document, showCarbonLabels, angle);
        double pad2 = labelBoundaryDistance(to, document, showCarbonLabels, angle + Math.PI);
        if (pad1 + pad2 + 4 > length) {
            double scale = Math.max(0, length - 4) / (pad1 + pad2);
            pad1 *= scale;
            pad2 *= scale;
        }
        double sx = from.getX() + ux * pad1;
        double sy = from.getY() + uy * pad1;
        double ex = to.getX() - ux * pad2;
        double ey = to.getY() - uy * pad2;
        double len = Math.hypot(ex - sx, ey - sy);
        if (len < 1) {
            return;
        }
        String color = "#2f3b4c";
        switch (bond.getType()) {
            case SINGLE -> line(svg, sx, sy, ex, ey, color, 2.25, null);
            case DOUBLE -> {
                line(svg, sx, sy, ex, ey, color, 2.25, null);
                double[] secondary = BondGeometry.doubleSecondaryLine(
                        document, bond, sx, sy, ex, ey);
                if (secondary != null) {
                    line(svg, secondary[0], secondary[1], secondary[2], secondary[3],
                            color, 2.25, null);
                }
                for (double[] connector : BondGeometry.doubleJunctionConnectors(
                        document, bond, sx, sy, ex, ey)) {
                    line(svg, connector[0], connector[1], connector[2], connector[3],
                            color, 2.25, null);
                }
            }
            case TRIPLE -> {
                line(svg, sx, sy, sx + ux * len, sy + uy * len, color, 2.25, null);
                double px = -uy * 4;
                double py = ux * 4;
                line(svg, sx + px, sy + py, sx + ux * len + px, sy + uy * len + py, color, 2.25, null);
                line(svg, sx - px, sy - py, sx + ux * len - px, sy + uy * len - py, color, 2.25, null);
            }
            case WEDGE -> {
                double px = -uy * Math.min(7.5, Math.max(4, len * 0.12));
                double py = ux * Math.min(7.5, Math.max(4, len * 0.12));
                svg.append(String.format(Locale.ROOT,
                        "    <polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" fill=\"%s\"/>\n",
                        sx, sy, sx + ux * len + px, sy + uy * len + py,
                        sx + ux * len - px, sy + uy * len - py, color));
            }
            case DASHED -> {
                int segments = Math.max(5, (int) (len / 8));
                for (int i = 0; i < segments; i++) {
                    double t = (i + 0.6) / segments;
                    double cx = sx + ux * len * t;
                    double cy = sy + uy * len * t;
                    double half = 1 + 4.8 * t;
                    line(svg, cx + uy * half, cy - ux * half,
                            cx - uy * half, cy + ux * half, color, 1.8, null);
                }
            }
            case WAVY -> {
                StringBuilder path = new StringBuilder();
                int samples = Math.max(24, (int) len / 3);
                for (int i = 0; i <= samples; i++) {
                    double t = i / (double) samples;
                    double wave = Math.sin(t * Math.PI * 10) * 2.9;
                    path.append(i == 0 ? "M" : " L")
                            .append(fmt(sx + ux * len * t - uy * wave)).append(',')
                            .append(fmt(sy + uy * len * t + ux * wave));
                }
                svg.append("    <path d=\"").append(path)
                        .append("\" fill=\"none\" stroke=\"").append(color)
                        .append("\" stroke-width=\"2.25\"/>\n");
            }
            case HYDROGEN -> {
                for (double[] dash : BondGeometry.horizontalDashes(sx, sy, ex, ey)) {
                    line(svg, dash[0], dash[1], dash[2], dash[3],
                            "#64748b", 2, null);
                }
            }
            case AROMATIC, RESONANCE -> {
                line(svg, sx, sy, ex, ey, color, 2.25, null);
                double px = -uy * 3.4;
                double py = ux * 3.4;
                String dash = bond.getType() == com.molview.app.model.BondType.AROMATIC
                        ? "4.5 4" : "1.5 4";
                line(svg, sx + px, sy + py, sx + ux * len + px, sy + uy * len + py,
                        color, 1.9, dash);
            }
        }
    }

    private static void line(StringBuilder svg, double x1, double y1, double x2, double y2,
                             String color, double width, String dash) {
        svg.append(String.format(Locale.ROOT,
                "    <line x1=\"%.2f\" y1=\"%.2f\" x2=\"%.2f\" y2=\"%.2f\" stroke=\"%s\" stroke-width=\"%.2f\"%s/>\n",
                x1, y1, x2, y2, color, width,
                dash == null ? "" : " stroke-dasharray=\"" + dash + "\""));
    }

    private static void appendCharge(StringBuilder svg, ChargeMark charge) {
        String color = switch (charge.getKind()) {
            case POSITIVE -> "#1d4ed8";
            case NEGATIVE -> "#b91c1c";
            case PARTIAL_POSITIVE -> "#b45309";
            case PARTIAL_NEGATIVE -> "#0f766e";
        };
        svg.append(String.format(Locale.ROOT,
                "    <text x=\"%.2f\" y=\"%.2f\" font-family=\"%s\" font-size=\"16\" font-weight=\"700\" " +
                        "fill=\"%s\" text-anchor=\"middle\" dominant-baseline=\"central\">%s</text>\n",
                charge.getX(), charge.getY(), SANS, color, xml(charge.getKind().label())));
    }

    private static void appendElectron(StringBuilder svg, ElectronMark mark,
                                       ChemDocument document) {
        if (mark.getKind() == ElectronKind.LONE_PAIR) {
            double[] offset = ChemRenderer.lonePairDotOffset(mark, document);
            svg.append(String.format(Locale.ROOT,
                    "    <circle cx=\"%.2f\" cy=\"%.2f\" r=\"2.35\" fill=\"#1f2937\"/>\n",
                    mark.getX() + offset[0], mark.getY() + offset[1]));
            svg.append(String.format(Locale.ROOT,
                    "    <circle cx=\"%.2f\" cy=\"%.2f\" r=\"2.35\" fill=\"#1f2937\"/>\n",
                    mark.getX() - offset[0], mark.getY() - offset[1]));
        } else {
            svg.append(String.format(Locale.ROOT,
                    "    <circle cx=\"%.2f\" cy=\"%.2f\" r=\"2.8\" fill=\"#1f2937\"/>\n",
                    mark.getX(), mark.getY()));
        }
    }

    private static void appendTextLabel(StringBuilder svg, TextLabel label) {
        svg.append(String.format(Locale.ROOT,
                "    <text x=\"%.2f\" y=\"%.2f\" font-family=\"%s\" font-size=\"%.2f\" font-weight=\"%s\" " +
                        "fill=\"%s\" text-anchor=\"middle\" dominant-baseline=\"central\">%s</text>\n",
                label.getX(), label.getY(), SANS, label.getFontSize(),
                label.isBold() ? "700" : "400", label.getColor(), xml(label.getText())));
    }

    private static void appendArrow(StringBuilder svg, Arrow arrow) {
        String color = "#263244";
        String d;
        if (arrow.isCurved()) {
            d = String.format(Locale.ROOT, "M%.2f,%.2f Q%.2f,%.2f %.2f,%.2f",
                    arrow.getX1(), arrow.getY1(), arrow.getControlX(), arrow.getControlY(),
                    arrow.getX2(), arrow.getY2());
            double[][] points = arrow.curvedPoints();
            double[] previous = points[points.length - 2];
            double[] tip = points[points.length - 1];
            double angle = Math.atan2(tip[1] - previous[1], tip[0] - previous[0]);
            appendArrowHead(svg, tip, angle, arrow.getType().hasFullHead());
        } else {
            d = String.format(Locale.ROOT, "M%.2f,%.2f L%.2f,%.2f",
                    arrow.getX1(), arrow.getY1(), arrow.getX2(), arrow.getY2());
            double angle = Math.atan2(arrow.getY2() - arrow.getY1(),
                    arrow.getX2() - arrow.getX1());
            appendArrowHead(svg, new double[]{arrow.getX2(), arrow.getY2()}, angle,
                    arrow.getType().hasFullHead());
        }
        svg.append("    <path d=\"").append(d)
                .append("\" fill=\"none\" stroke=\"").append(color)
                .append("\" stroke-width=\"2.2\"/>\n");
    }

    private static void appendArrowHead(StringBuilder svg, double[] tip, double angle, boolean full) {
        if (full) {
            double backX = tip[0] - Math.cos(angle) * 13;
            double backY = tip[1] - Math.sin(angle) * 13;
            double px = -Math.sin(angle) * 5.6;
            double py = Math.cos(angle) * 5.6;
            svg.append(String.format(Locale.ROOT,
                    "    <polygon points=\"%.2f,%.2f %.2f,%.2f %.2f,%.2f\" fill=\"#263244\"/>\n",
                    tip[0], tip[1], backX + px, backY + py, backX - px, backY - py));
        } else {
            double backAngle = angle + Math.PI - 0.62;
            double x = tip[0] + Math.cos(backAngle) * 13;
            double y = tip[1] + Math.sin(backAngle) * 13;
            line(svg, tip[0], tip[1], x, y, "#263244", 2.4, null);
            double other = angle + Math.PI + 0.62;
            line(svg, tip[0], tip[1], tip[0] + Math.cos(other) * 5.5,
                    tip[1] + Math.sin(other) * 5.5, "#263244", 1.5, null);
        }
    }

    private static void appendBracket(StringBuilder svg, Bracket bracket) {
        double x = bracket.getX();
        double y = bracket.getY();
        double w = bracket.getWidth();
        double h = bracket.getHeight();
        String path = switch (bracket.getType()) {
            case SQUARE -> String.format(Locale.ROOT,
                    "M%.2f,%.2f L%.2f,%.2f L%.2f,%.2f L%.2f,%.2f M%.2f,%.2f L%.2f,%.2f L%.2f,%.2f L%.2f,%.2f",
                    x + 14, y, x, y, x, y + h, x + 14, y + h,
                    x + w - 14, y, x + w, y, x + w, y + h, x + w - 14, y + h);
            case ROUND -> String.format(Locale.ROOT,
                    "M%.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f " +
                            "M%.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f",
                    x + 18, y - 1, x - 5, y + h * 0.25, x + 4, y + h * 0.5,
                    x - 5, y + h * 0.75, x + 18, y + h + 1,
                    x + w - 18, y - 1, x + w + 5, y + h * 0.25, x + w - 4, y + h * 0.5,
                    x + w + 5, y + h * 0.75, x + w - 18, y + h + 1);
            case CURLY -> String.format(Locale.ROOT,
                    "M%.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f " +
                            "M%.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f Q%.2f,%.2f %.2f,%.2f",
                    x + w * 0.2, y, x + w * 0.05, y, x + w * 0.07, y + h * 0.28,
                    x + w * 0.08, y + h * 0.44, x + w * 0.01, y + h * 0.5,
                    x + w * 0.08, y + h * 0.56, x + w * 0.07, y + h * 0.72,
                    x + w * 0.05, y + h, x + w * 0.2, y + h,
                    x + w * 0.8, y, x + w * 0.95, y, x + w * 0.93, y + h * 0.28,
                    x + w * 0.92, y + h * 0.44, x + w * 0.99, y + h * 0.5,
                    x + w * 0.92, y + h * 0.56, x + w * 0.93, y + h * 0.72,
                    x + w * 0.95, y + h, x + w * 0.8, y + h);
        };
        svg.append("    <path d=\"").append(path)
                .append("\" fill=\"none\" stroke=\"#263244\" stroke-width=\"2.1\"/>\n");
        if (!bracket.getLabel().isBlank()) {
            svg.append(String.format(Locale.ROOT,
                    "    <text x=\"%.2f\" y=\"%.2f\" font-family=\"%s\" font-size=\"16\" font-weight=\"600\" " +
                            "fill=\"#334155\" text-anchor=\"middle\" dominant-baseline=\"central\">%s</text>\n",
                    bracket.labelX(), bracket.labelY(), SANS, xml(bracket.getLabel())));
        }
    }

    private static double elementWidth(Atom atom) {
        return Math.max(1, atom.getElementSymbol().length()) * 9.2;
    }

    private static double[] labelXBounds(Atom atom, ChemDocument document) {
        int length = Math.max(1, ImplicitHydrogens.label(atom, document).length());
        double total = 11 + (length - 1) * 9.2;
        ImplicitHydrogens.Orientation orientation = ImplicitHydrogens.orientation(atom, document);
        if (atom.isGeneric() || "H".equals(atom.getElementSymbol())
                || orientation == ImplicitHydrogens.Orientation.CENTER) {
            return new double[]{-total / 2, total / 2};
        }
        double element = elementWidth(atom);
        if (orientation == ImplicitHydrogens.Orientation.PREFIX) {
            return new double[]{element / 2 - total, element / 2};
        }
        return new double[]{-element / 2, total - element / 2};
    }

    private static double labelBoundaryDistance(Atom atom, ChemDocument document,
                                                boolean showCarbonLabels, double angle) {
        if (!ChemRenderer.isLabelVisible(atom, document, showCarbonLabels)) {
            return 0;
        }
        double[] bounds = labelXBounds(atom, document);
        double halfHeight = 10;
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

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
