package com.molview.app.model;

/** Chemically meaningful bond representations available in the editor. */
public enum BondType {
    SINGLE("Single", "—", 1.0),
    DOUBLE("Double", "=", 2.0),
    TRIPLE("Triple", "≡", 3.0),
    WEDGE("Solid wedge", "▲", 1.0),
    DASHED("Hashed wedge", "△", 1.0),
    WAVY("Wavy / unknown", "~", 1.0),
    HYDROGEN("Hydrogen bond", "⋯", 1.0),
    AROMATIC("Aromatic", "⌬", 1.5),
    RESONANCE("Resonance / delocalised", "⇌", 1.5);

    private final String displayName;
    private final String glyph;
    private final double order;

    BondType(String displayName, String glyph, double order) {
        this.displayName = displayName;
        this.glyph = glyph;
        this.order = order;
    }

    public String displayName() {
        return displayName;
    }

    public String glyph() {
        return glyph;
    }

    /** Approximate bond order, used by SMILES and conjugation checks. */
    public double order() {
        return order;
    }

    public boolean isAromaticOrDelocalised() {
        return this == AROMATIC || this == RESONANCE;
    }

    public boolean isStereochemical() {
        return this == WEDGE || this == DASHED || this == WAVY;
    }

    public int molBondType() {
        return switch (this) {
            case SINGLE, WEDGE, DASHED, WAVY, HYDROGEN -> 1;
            case DOUBLE -> 2;
            case TRIPLE -> 3;
            case AROMATIC, RESONANCE -> 4;
        };
    }

    public int molStereo() {
        return switch (this) {
            case WEDGE -> 1;
            case DASHED -> 6;
            case WAVY -> 4;
            default -> 0;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
