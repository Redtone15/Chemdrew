package com.molview.app.model;

public enum ArrowType {
    STRAIGHT("Reaction arrow", "→", true),
    CURVED("Curved electron-pair arrow", "↷", true),
    FISHHOOK("Single-electron fishhook", "↪", false);

    private final String displayName;
    private final String glyph;
    private final boolean fullHead;

    ArrowType(String displayName, String glyph, boolean fullHead) {
        this.displayName = displayName;
        this.glyph = glyph;
        this.fullHead = fullHead;
    }

    public String displayName() {
        return displayName;
    }

    public String glyph() {
        return glyph;
    }

    public boolean hasFullHead() {
        return fullHead;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
