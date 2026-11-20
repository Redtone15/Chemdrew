package com.molview.app.model;

/** Immutable axis-aligned rectangle in drawing (world) coordinates. */
public final class Bounds {
    public final double minX;
    public final double minY;
    public final double maxX;
    public final double maxY;

    public Bounds(double minX, double minY, double maxX, double maxY) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
    }

    public static Bounds point(double x, double y) {
        return new Bounds(x, y, x, y);
    }

    public static Bounds of(double x, double y, double w, double h) {
        return new Bounds(x, y, x + w, y + h);
    }

    public double width() {
        return maxX - minX;
    }

    public double height() {
        return maxY - minY;
    }

    public double centerX() {
        return (minX + maxX) / 2.0;
    }

    public double centerY() {
        return (minY + maxY) / 2.0;
    }

    public Bounds union(Bounds other) {
        if (other == null) {
            return this;
        }
        return new Bounds(
                Math.min(minX, other.minX),
                Math.min(minY, other.minY),
                Math.max(maxX, other.maxX),
                Math.max(maxY, other.maxY));
    }

    public Bounds expand(double dx, double dy) {
        return new Bounds(minX - dx, minY - dy, maxX + dx, maxY + dy);
    }

    public Bounds expand(double amount) {
        return expand(amount, amount);
    }

    public boolean contains(double x, double y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public boolean intersects(Bounds other) {
        return maxX >= other.minX && other.maxX >= minX
                && maxY >= other.minY && other.maxY >= minY;
    }

    @Override
    public String toString() {
        return String.format("Bounds[%.1f,%.1f %.1fx%.1f]", minX, minY, width(), height());
    }
}
