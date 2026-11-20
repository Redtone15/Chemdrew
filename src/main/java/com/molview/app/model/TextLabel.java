package com.molview.app.model;

/** Free text annotation (repetition index, substituent label, note, ...). */
public final class TextLabel extends Annotation {
    private String text;
    private double x;
    private double y;
    private double fontSize;
    private boolean bold;
    private String color;

    public TextLabel(String text, double x, double y) {
        this(null, text, x, y, 17, false, "#253047");
    }

    public TextLabel(String id, String text, double x, double y,
                     double fontSize, boolean bold, String color) {
        super(id == null ? Ids.next("t") : id);
        this.text = text == null ? "" : text;
        this.x = x;
        this.y = y;
        this.fontSize = fontSize <= 0 ? 17 : fontSize;
        this.bold = bold;
        this.color = color == null ? "#253047" : color;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
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

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = Math.max(6, fontSize);
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color == null ? "#253047" : color;
    }

    public double estimatedWidth() {
        return Math.max(10, text.length() * fontSize * (bold ? 0.64 : 0.61));
    }

    public double estimatedHeight() {
        return fontSize * 1.25;
    }

    @Override
    public Bounds bounds() {
        double w = estimatedWidth() / 2;
        double h = estimatedHeight() / 2;
        return new Bounds(x - w, y - h, x + w, y + h);
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
    public TextLabel copy(boolean preserveId) {
        TextLabel copy = new TextLabel(preserveId ? getId() : null, text, x, y, fontSize, bold, color);
        return copyAnnotation(copy, preserveId);
    }

    @Override
    public String typeName() {
        return "Text label";
    }
}
