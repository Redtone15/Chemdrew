package com.molview.app.model;

/**
 * Formula-style atom labels with implicit hydrogens for the editor's main-group
 * elements. Hydrogens are inferred from the usual valence and the current bond
 * order sum; a formal charge adjusts the valence so ions such as O− do not gain
 * an extra implicit hydrogen. The element symbol is anchored toward the bond
 * side and the hydrogens are placed on the other side.
 */
public final class ImplicitHydrogens {
    private static final String SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉";

    public enum Orientation {
        CENTER,
        PREFIX,
        SUFFIX
    }

    private ImplicitHydrogens() {
    }

    public static int count(Atom atom, ChemDocument document) {
        if (atom == null || atom.isGeneric()) {
            return 0;
        }
        String symbol = atom.getElementSymbol();
        int valence = valence(symbol, document == null ? 0 : document.formalCharge(atom));
        if (valence <= 0) {
            return 0;
        }
        double used = 0;
        if (document != null) {
            for (Bond bond : document.bondsOf(atom)) {
                if (bond.getType() != BondType.HYDROGEN) {
                    used += bond.getType().order();
                }
            }
        }
        return Math.max(0, valence - (int) Math.round(used));
    }

    /**
     * Returns the label with the element symbol in the stable position and the
     * hydrogen formula on the side away from the incident bond(s).
     */
    public static String label(Atom atom, ChemDocument document) {
        if (atom == null) {
            return "";
        }
        if (atom.isGeneric()) {
            return atom.displayLabel();
        }
        int hydrogens = count(atom, document);
        if (hydrogens <= 0) {
            return atom.getElementSymbol();
        }
        String symbol = atom.getElementSymbol();
        if ("H".equals(symbol)) {
            return "H₂";
        }
        String h = hydrogenText(hydrogens);
        Orientation orientation = orientation(atom, document);
        if (orientation == Orientation.CENTER) {
            orientation = defaultOrientation(symbol, hydrogens);
        }
        return orientation == Orientation.PREFIX ? h + symbol : symbol + h;
    }

    /**
     * The side of the label on which hydrogens are placed. A single bond to the
     * right puts the element on the right and the H formula on the left; a bond
     * to the left does the opposite. With no strong horizontal direction the
     * conventional formula orientation is used.
     */
    public static Orientation orientation(Atom atom, ChemDocument document) {
        if (atom == null || atom.isGeneric()
                || "H".equals(atom.getElementSymbol())
                || count(atom, document) <= 0) {
            return Orientation.CENTER;
        }
        double netX = 0;
        int bonds = 0;
        if (document != null) {
            for (Bond bond : document.bondsOf(atom)) {
                if (bond.getType() == BondType.HYDROGEN) {
                    continue;
                }
                Atom other = bond.other(atom);
                double dx = other.getX() - atom.getX();
                double dy = other.getY() - atom.getY();
                double length = Math.hypot(dx, dy);
                if (length > 1e-6) {
                    netX += dx / length;
                    bonds++;
                }
            }
        }
        if (bonds == 0) {
            return Orientation.CENTER;
        }
        if (netX > 0.2) {
            return Orientation.PREFIX;
        }
        if (netX < -0.2) {
            return Orientation.SUFFIX;
        }
        return defaultOrientation(atom.getElementSymbol(), count(atom, document));
    }

    public static String hydrogenText(int count) {
        return count <= 0 ? "" : count == 1 ? "H" : "H" + subscript(count);
    }

    public static String elementText(Atom atom) {
        return atom == null ? "" : atom.getElementSymbol();
    }

    private static Orientation defaultOrientation(String symbol, int hydrogens) {
        return switch (symbol) {
            case "O", "S", "Se", "Te" -> hydrogens >= 2
                    ? Orientation.PREFIX : Orientation.SUFFIX;
            default -> Orientation.SUFFIX;
        };
    }

    private static int valence(String symbol, int formalCharge) {
        int valence = switch (symbol) {
            case "H" -> 1 - Math.abs(formalCharge);
            case "B" -> 3 - formalCharge;
            case "C", "Si" -> 4 - Math.abs(formalCharge);
            case "N", "P", "As" -> 3 + formalCharge;
            case "O", "S", "Se", "Te" -> 2 + formalCharge;
            case "F", "Cl", "Br", "I", "At" -> 1 + formalCharge;
            default -> 0;
        };
        return Math.max(0, valence);
    }

    private static String subscript(int number) {
        StringBuilder text = new StringBuilder();
        for (char digit : Integer.toString(number).toCharArray()) {
            text.append(SUBSCRIPTS.charAt(digit - '0'));
        }
        return text.toString();
    }
}
