package com.molview.app.render;

import com.molview.app.model.Atom;
import com.molview.app.model.Bond;
import com.molview.app.model.BondType;
import com.molview.app.model.ChemDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared visual geometry for bond styles that depend on their neighbours.
 * Keeping this in one place lets the canvas/PDF renderer and the SVG exporter
 * produce the same double-bond junction and hydrogen-bond dash treatment.
 */
public final class BondGeometry {
    public static final double DOUBLE_OFFSET = 3.2;
    public static final double DOUBLE_JUNCTION_GAP = 7.0;

    private BondGeometry() {
    }

    /**
     * Returns the offset secondary line of a double bond. The line is gap-trimmed
     * at an atom where exactly one other single bond meets the double bond, so
     * that only the centre line continues into that single bond. When two single
     * bonds sit on the same side of that atom, the secondary line continues all
     * the way in and visually joins both.
     */
    public static double[] doubleSecondaryLine(ChemDocument document, Bond bond,
                                               double sx, double sy,
                                               double ex, double ey) {
        double dx = ex - sx;
        double dy = ey - sy;
        double length = Math.hypot(dx, dy);
        if (length < 0.001) {
            return null;
        }
        double ux = dx / length;
        double uy = dy / length;
        int side = chooseDoubleSide(document, bond, ux, uy);
        double px = -uy * DOUBLE_OFFSET * side;
        double py = ux * DOUBLE_OFFSET * side;

        boolean carbonylFrom = isCarbonylCarbon(document, bond, bond.getFrom(), bond.getTo());
        boolean carbonylTo = isCarbonylCarbon(document, bond, bond.getTo(), bond.getFrom());
        double startGap = junctionGap(document, bond, bond.getFrom(), carbonylFrom);
        double endGap = junctionGap(document, bond, bond.getTo(), carbonylTo);
        double startT = startGap;
        double endT = length - endGap;
        if (endT - startT < 2.0) {
            return null;
        }
        return new double[]{
                sx + ux * startT + px, sy + uy * startT + py,
                sx + ux * endT + px, sy + uy * endT + py
        };
    }

    /**
     * At the carbon of a ketone the second C=O line is joined back to the
     * carbonyl carbon, so both double-bond strokes meet the two single bonds at
     * that vertex. This treatment is deliberately limited to a C=O whose other
     * side bears two or more single bonds.
     */
    public static List<double[]> doubleJunctionConnectors(ChemDocument document, Bond bond,
                                                           double sx, double sy,
                                                           double ex, double ey) {
        List<double[]> connectors = new ArrayList<>();
        boolean carbonylFrom = isCarbonylCarbon(document, bond, bond.getFrom(), bond.getTo());
        boolean carbonylTo = isCarbonylCarbon(document, bond, bond.getTo(), bond.getFrom());
        if (!carbonylFrom && !carbonylTo) {
            return connectors;
        }
        double[] secondary = doubleSecondaryLine(document, bond, sx, sy, ex, ey);
        if (secondary == null) {
            return connectors;
        }
        if (carbonylFrom) {
            connectors.add(new double[]{sx, sy, secondary[0], secondary[1]});
        }
        if (carbonylTo) {
            connectors.add(new double[]{ex, ey, secondary[2], secondary[3]});
        }
        return connectors;
    }

    private static boolean isCarbonylCarbon(ChemDocument document, Bond bond,
                                            Atom atom, Atom other) {
        return "O".equals(other.getElementSymbol())
                && atom.isCarbon()
                && singleBondCount(document, atom, bond) >= 2;
    }

    private static int singleBondCount(ChemDocument document, Atom atom, Bond ignored) {
        int count = 0;
        for (Bond other : document.bondsOf(atom)) {
            if (other != ignored && other.getType() == BondType.SINGLE) {
                count++;
            }
        }
        return count;
    }

    private static int chooseDoubleSide(ChemDocument document, Bond bond,
                                        double ux, double uy) {
        int positive = 0;
        int negative = 0;
        boolean twoOnPositive = false;
        boolean twoOnNegative = false;
        for (Atom atom : new Atom[]{bond.getFrom(), bond.getTo()}) {
            int sidePositive = 0;
            int sideNegative = 0;
            for (Bond other : document.bondsOf(atom)) {
                if (other == bond || other.getType() != BondType.SINGLE) {
                    continue;
                }
                Atom neighbour = other.other(atom);
                double cross = ux * (neighbour.getY() - atom.getY())
                        - uy * (neighbour.getX() - atom.getX());
                if (cross > 1.0) {
                    sidePositive++;
                } else if (cross < -1.0) {
                    sideNegative++;
                }
            }
            positive += sidePositive;
            negative += sideNegative;
            twoOnPositive |= sidePositive >= 2;
            twoOnNegative |= sideNegative >= 2;
        }
        // Two single bonds on one face are intended to join both double-bond
        // lines; otherwise keep the secondary line on the emptier face.
        if (twoOnPositive != twoOnNegative) {
            return twoOnPositive ? 1 : -1;
        }
        return positive <= negative ? 1 : -1;
    }

    private static double junctionGap(ChemDocument document, Bond bond, Atom atom,
                                      boolean carbonylCarbon) {
        if (carbonylCarbon) {
            return 0;
        }
        for (Bond other : document.bondsOf(atom)) {
            if (other != bond && other.getType() == BondType.SINGLE) {
                // Away from the ketone case, the secondary stroke must not run
                // into a single bond at this atom.
                return DOUBLE_JUNCTION_GAP;
            }
        }
        return 0;
    }

    /**
     * Short horizontal strokes distributed along the hydrogen bond. The strokes
     * stay horizontal even when the bond itself is diagonal.
     */
    public static List<double[]> horizontalDashes(double sx, double sy,
                                                  double ex, double ey) {
        List<double[]> dashes = new ArrayList<>();
        double dx = ex - sx;
        double dy = ey - sy;
        double length = Math.hypot(dx, dy);
        if (length < 1.0) {
            return dashes;
        }
        double ux = dx / length;
        double uy = dy / length;
        int count = Math.max(3, (int) Math.round(length / 11.0));
        double dash = Math.min(7.2, length / count * 0.62);
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            double cx = sx + ux * length * t;
            double cy = sy + uy * length * t;
            dashes.add(new double[]{cx - dash / 2, cy, cx + dash / 2, cy});
        }
        return dashes;
    }
}
