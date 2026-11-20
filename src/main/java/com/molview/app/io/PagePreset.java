package com.molview.app.io;

/** Common print page sizes in PostScript points (1/72 inch). */
public enum PagePreset {
    A4("A4", 595.28, 841.89),
    LETTER("US Letter", 612.0, 792.0),
    A3("A3", 841.89, 1190.55),
    LEGAL("US Legal", 612.0, 1008.0),
    CUSTOM("Custom", 595.28, 841.89);

    private final String displayName;
    private final double widthPt;
    private final double heightPt;

    PagePreset(String displayName, double widthPt, double heightPt) {
        this.displayName = displayName;
        this.widthPt = widthPt;
        this.heightPt = heightPt;
    }

    public String displayName() {
        return displayName;
    }

    public double widthPt() {
        return widthPt;
    }

    public double heightPt() {
        return heightPt;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
