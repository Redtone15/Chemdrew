package com.molview.app.model;

/** An independently movable formal or partial charge label. */
public final class ChargeMark extends Annotation {
    private ChargeKind kind;
    private double x;
    private double y;

    public ChargeMark(ChargeKind kind, double x, double y) {
        this(null, kind, x, y);
    }

    public ChargeMark(String id, ChargeKind kind, double x, double y) {
        super(id == null ? Ids.next("c") : id);
        this.kind = kind == null ? ChargeKind.POSITIVE : kind;
        this.x = x;
        this.y = y;
    }

    public ChargeKind getKind() {
        return kind;
    }

    public void setKind(ChargeKind kind) {
        this.kind = kind == null ? ChargeKind.POSITIVE : kind;
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

    private double halfWidth() {
        return kind.isPartial() ? 13 : 8;
    }

    @Override
    public Bounds bounds() {
        return new Bounds(x - halfWidth(), y - 11, x + halfWidth(), y + 11);
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
    public ChargeMark copy(boolean preserveId) {
        ChargeMark copy = new ChargeMark(preserveId ? getId() : null, kind, x, y);
        return copyAnnotation(copy, preserveId);
    }

    @Override
    public String typeName() {
        return kind.isPartial() ? "Partial charge " + kind.label() : "Charge " + kind.label();
    }
}
