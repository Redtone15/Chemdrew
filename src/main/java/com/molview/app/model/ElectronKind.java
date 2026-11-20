package com.molview.app.model;

public enum ElectronKind {
    LONE_PAIR("Lone pair", ":"),
    RADICAL("Single electron", "•");

    private final String displayName;
    private final String glyph;

    ElectronKind(String displayName, String glyph) {
        this.displayName = displayName;
        this.glyph = glyph;
    }

    public String displayName() {
        return displayName;
    }

    public String glyph() {
        return glyph;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
