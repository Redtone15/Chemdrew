package com.molview.app.model;

import java.util.Map;

/** A bond is defined by its endpoint atoms so moving atoms automatically moves it. */
public final class Bond extends ChemObject {
    private final Atom from;
    private final Atom to;
    private BondType type;

    public Bond(Atom from, Atom to, BondType type) {
        this(null, from, to, type);
    }

    public Bond(String id, Atom from, Atom to, BondType type) {
        super(id == null ? Ids.next("b") : id);
        if (from == to) {
            throw new IllegalArgumentException("A bond needs two distinct atoms");
        }
        this.from = from;
        this.to = to;
        this.type = type == null ? BondType.SINGLE : type;
    }

    public Atom getFrom() {
        return from;
    }

    public Atom getTo() {
        return to;
    }

    public BondType getType() {
        return type;
    }

    public void setType(BondType type) {
        this.type = type == null ? BondType.SINGLE : type;
    }

    public Atom other(Atom atom) {
        return atom == from ? to : from;
    }

    public boolean connects(Atom atom) {
        return atom == from || atom == to;
    }

    public double length() {
        return Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
    }

    /** Signed angle of the bond in radians (screen coordinates). */
    public double angle() {
        return Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
    }

    @Override
    public Bounds bounds() {
        return new Bounds(from.getX(), from.getY(), to.getX(), to.getY()).expand(4);
    }

    @Override
    public void translate(double dx, double dy) {
        // Bonds follow their endpoint atoms. The document moves endpoints instead.
    }

    @Override
    public boolean hitTest(double x, double y, double tolerance) {
        return Geometry.distanceToSegment(x, y, from.getX(), from.getY(), to.getX(), to.getY())
                <= 6 + tolerance;
    }

    @Override
    public Bond copy(boolean preserveId) {
        throw new UnsupportedOperationException("Use copy(atomMap)");
    }

    public Bond copy(Map<Atom, Atom> atomMap, boolean preserveId) {
        Bond copy = new Bond(preserveId ? getId() : null,
                atomMap.get(from), atomMap.get(to), type);
        return copyBase(copy, preserveId);
    }

    @Override
    public String typeName() {
        return type.displayName() + " bond";
    }
}
