package com.molview.app.model;

import java.util.Objects;

/** A chemical atom or a generic R/X substituent at a position on the canvas. */
public final class Atom extends ChemObject {
    private String elementSymbol;
    private AtomKind kind;
    private String genericLabel;
    private double x;
    private double y;

    public Atom(String symbol, AtomKind kind, String label, double x, double y) {
        this(null, symbol, kind, label, x, y);
    }

    public Atom(String id, String symbol, AtomKind kind, String label, double x, double y) {
        super(id == null ? Ids.next("a") : id);
        this.elementSymbol = Objects.requireNonNullElse(symbol, "C");
        this.kind = kind == null ? AtomKind.ELEMENT : kind;
        this.genericLabel = label;
        this.x = x;
        this.y = y;
    }

    public static Atom element(String symbol, double x, double y) {
        return new Atom(symbol, AtomKind.ELEMENT, null, x, y);
    }

    public static Atom rGroup(String label, double x, double y) {
        return new Atom("R", AtomKind.R_GROUP, label == null || label.isBlank() ? "R" : label, x, y);
    }

    public static Atom xGroup(String label, double x, double y) {
        return new Atom("X", AtomKind.X_GROUP, label == null || label.isBlank() ? "X" : label, x, y);
    }

    public String getElementSymbol() {
        return elementSymbol;
    }

    public void setElementSymbol(String elementSymbol) {
        this.elementSymbol = Objects.requireNonNull(elementSymbol);
        if (kind == AtomKind.ELEMENT && (elementSymbol.equals("R") || elementSymbol.equals("X"))) {
            kind = elementSymbol.equals("R") ? AtomKind.R_GROUP : AtomKind.X_GROUP;
            if (genericLabel == null) {
                genericLabel = elementSymbol;
            }
        }
    }

    public AtomKind getKind() {
        return kind;
    }

    public void setKind(AtomKind kind) {
        this.kind = Objects.requireNonNull(kind);
        if (kind.isGeneric() && (genericLabel == null || genericLabel.isBlank())) {
            genericLabel = kind == AtomKind.R_GROUP ? "R" : "X";
        }
    }

    public String getGenericLabel() {
        return genericLabel;
    }

    public void setGenericLabel(String genericLabel) {
        this.genericLabel = genericLabel;
    }

    /** Text drawn at the atom position. */
    public String displayLabel() {
        if (kind.isGeneric()) {
            return genericLabel == null || genericLabel.isBlank()
                    ? (kind == AtomKind.R_GROUP ? "R" : "X")
                    : genericLabel;
        }
        return elementSymbol;
    }

    /** Atom symbol suitable for chemical file formats; generic groups become wildcards. */
    public String exportSymbol() {
        return kind.isGeneric() ? "*" : elementSymbol;
    }

    public boolean isGeneric() {
        return kind.isGeneric();
    }

    public boolean isCarbon() {
        return kind == AtomKind.ELEMENT && "C".equals(elementSymbol);
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public Bounds bounds() {
        return new Bounds(x - 12, y - 13, x + 12, y + 13);
    }

    @Override
    public void translate(double dx, double dy) {
        x += dx;
        y += dy;
    }

    @Override
    public boolean hitTest(double px, double py, double tolerance) {
        double radius = 13 + tolerance;
        double ddx = px - x;
        double ddy = py - y;
        return ddx * ddx + ddy * ddy <= radius * radius;
    }

    @Override
    public Atom copy(boolean preserveId) {
        Atom copy = new Atom(preserveId ? getId() : null, elementSymbol, kind, genericLabel, x, y);
        copyBase(copy, preserveId);
        return copy;
    }

    @Override
    public String typeName() {
        return kind.isGeneric() ? "Group " + displayLabel() : "Atom " + displayLabel();
    }
}
