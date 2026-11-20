package com.molview.app.model;

/**
 * Reaction/mechanism arrow. Curved and fishhook arrows use a quadratic Bezier;
 * the control point is independently draggable.
 */
public final class Arrow extends ChemObject {
    private ArrowType type;
    private double x1;
    private double y1;
    private double cx;
    private double cy;
    private double x2;
    private double y2;

    public Arrow(ArrowType type, double x1, double y1, double x2, double y2) {
        this(null, type, x1, y1,
                (x1 + x2) / 2 + (y2 - y1) * 0.18,
                (y1 + y2) / 2 - (x2 - x1) * 0.18,
                x2, y2);
    }

    public Arrow(String id, ArrowType type,
                 double x1, double y1, double cx, double cy, double x2, double y2) {
        super(id == null ? Ids.next("r") : id);
        this.type = type == null ? ArrowType.STRAIGHT : type;
        setPoints(x1, y1, cx, cy, x2, y2);
    }

    public ArrowType getType() {
        return type;
    }

    public void setType(ArrowType type) {
        this.type = type == null ? ArrowType.STRAIGHT : type;
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getControlX() {
        return cx;
    }

    public double getControlY() {
        return cy;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    public void setPoints(double x1, double y1, double cx, double cy, double x2, double y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.cx = cx;
        this.cy = cy;
        this.x2 = x2;
        this.y2 = y2;
    }

    public void setStart(double x, double y) {
        double dx = x - x1;
        double dy = y - y1;
        x1 = x;
        y1 = y;
        cx += dx;
        cy += dy;
    }

    public void setEnd(double x, double y) {
        x2 = x;
        y2 = y;
    }

    public void setControl(double x, double y) {
        cx = x;
        cy = y;
    }

    public boolean isCurved() {
        return type != ArrowType.STRAIGHT;
    }

    public double[][] curvedPoints() {
        if (!isCurved()) {
            return new double[][]{{x1, y1}, {x2, y2}};
        }
        return Geometry.quadratic(x1, y1, cx, cy, x2, y2, 28);
    }

    @Override
    public Bounds bounds() {
        Bounds bounds;
        if (isCurved()) {
            bounds = Geometry.polylineBounds(curvedPoints());
        } else {
            bounds = new Bounds(x1, y1, x2, y2);
        }
        return bounds.expand(10);
    }

    @Override
    public void translate(double dx, double dy) {
        x1 += dx;
        y1 += dy;
        cx += dx;
        cy += dy;
        x2 += dx;
        y2 += dy;
    }

    @Override
    public boolean hitTest(double px, double py, double tolerance) {
        return Geometry.distanceToPolyline(px, py, curvedPoints()) <= 8 + tolerance;
    }

    @Override
    public Arrow copy(boolean preserveId) {
        Arrow copy = new Arrow(preserveId ? getId() : null, type, x1, y1, cx, cy, x2, y2);
        return copyBase(copy, preserveId);
    }

    @Override
    public String typeName() {
        return type.displayName();
    }
}
