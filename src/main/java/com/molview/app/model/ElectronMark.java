package com.molview.app.model;

/** Lone-pair or unpaired-electron dots, independent from the bond graph. */
public final class ElectronMark extends Annotation {
    private ElectronKind kind;
    private double x;
    private double y;

    public ElectronMark(ElectronKind kind, double x, double y) {
        this(null, kind, x, y);
    }

    public ElectronMark(String id, ElectronKind kind, double x, double y) {
        super(id == null ? Ids.next("e") : id);
        this.kind = kind == null ? ElectronKind.LONE_PAIR : kind;
        this.x = x;
        this.y = y;
    }

    public ElectronKind getKind() {
        return kind;
    }

    public void setKind(ElectronKind kind) {
        this.kind = kind == null ? ElectronKind.LONE_PAIR : kind;
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
        return new Bounds(x - 10, y - 8, x + 10, y + 8);
    }

    @Override
    public void translate(double dx, double dy) {
        x += dx;
        y += dy;
    }

    @Override
    public boolean hitTest(double px, double py, double tolerance) {
        return bounds().expand(tolerance).contains(px, py);
    }

    @Override
    public ElectronMark copy(boolean preserveId) {
        ElectronMark copy = new ElectronMark(preserveId ? getId() : null, kind, x, y);
        return copyAnnotation(copy, preserveId);
    }

    @Override
    public String typeName() {
        return kind.displayName();
    }
}
