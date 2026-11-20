package com.molview.app.model;

/** Resizable grouping bracket with an optional annotation outside it. */
public final class Bracket extends ChemObject {
    private BracketType type;
    private double x;
    private double y;
    private double width;
    private double height;
    private String label;
    private BracketLabelPosition labelPosition;

    public Bracket(BracketType type, double x, double y, double width, double height) {
        this(null, type, x, y, width, height, "", BracketLabelPosition.BOTTOM_RIGHT);
    }

    public Bracket(String id, BracketType type, double x, double y, double width, double height,
                   String label, BracketLabelPosition labelPosition) {
        super(id == null ? Ids.next("k") : id);
        this.type = type == null ? BracketType.SQUARE : type;
        setFrame(x, y, width, height);
        this.label = label == null ? "" : label;
        this.labelPosition = labelPosition == null ? BracketLabelPosition.BOTTOM_RIGHT : labelPosition;
    }

    public BracketType getType() {
        return type;
    }

    public void setType(BracketType type) {
        this.type = type == null ? BracketType.SQUARE : type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public BracketLabelPosition getLabelPosition() {
        return labelPosition;
    }

    public void setLabelPosition(BracketLabelPosition labelPosition) {
        this.labelPosition = labelPosition == null
                ? BracketLabelPosition.BOTTOM_RIGHT : labelPosition;
    }

    public void setFrame(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(24, width);
        this.height = Math.max(24, height);
    }

    public void setLabelPoint(double x, double y, double width, double height) {
        // Position helpers below are used by the UI.
    }

    public double labelX() {
        if (label.isBlank()) {
            return x + width;
        }
        double offset = Math.max(10, label.length() * 8.5);
        return switch (labelPosition) {
            case TOP_RIGHT, BOTTOM_RIGHT -> x + width + offset;
            case TOP_LEFT, BOTTOM_LEFT -> x - offset;
        };
    }

    public double labelY() {
        if (label.isBlank()) {
            return y + height / 2;
        }
        return switch (labelPosition) {
            case TOP_RIGHT, TOP_LEFT -> y + 14;
            case BOTTOM_RIGHT, BOTTOM_LEFT -> y + height - 4;
        };
    }

    @Override
    public Bounds bounds() {
        Bounds result = new Bounds(x, y, x + width, y + height).expand(4);
        if (!label.isBlank()) {
            result = result.union(Bounds.point(labelX(), labelY()).expand(14));
        }
        return result;
    }

    @Override
    public void translate(double dx, double dy) {
        x += dx;
        y += dy;
    }

    @Override
    public boolean hitTest(double px, double py, double tolerance) {
        double t = 8 + tolerance;
        boolean nearLeft = Math.abs(px - x) <= t && py >= y - t && py <= y + height + t;
        boolean nearRight = Math.abs(px - (x + width)) <= t && py >= y - t && py <= y + height + t;
        boolean nearTop = Math.abs(py - y) <= t && px >= x - t && px <= x + width + t;
        boolean nearBottom = Math.abs(py - (y + height)) <= t && px >= x - t && px <= x + width + t;
        boolean nearLabel = !label.isBlank()
                && new Bounds(labelX() - 18, labelY() - 14, labelX() + 18, labelY() + 14)
                .expand(tolerance).contains(px, py);
        return nearLeft || nearRight || nearTop || nearBottom || nearLabel;
    }

    @Override
    public Bracket copy(boolean preserveId) {
        Bracket copy = new Bracket(preserveId ? getId() : null, type, x, y, width, height,
                label, labelPosition);
        return copyBase(copy, preserveId);
    }

    @Override
    public String typeName() {
        return "Bracket " + type.open() + "…" + type.close();
    }
}
