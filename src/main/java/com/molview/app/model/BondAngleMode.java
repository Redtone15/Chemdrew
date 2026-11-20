package com.molview.app.model;

/** Snap mode for newly drawn single bonds. */
public enum BondAngleMode {
    SNAP_60("60° snap"),
    FREE("Free"),
    CUSTOM("Custom"),
    HEX("Hex grid (120°)");

    private final String displayName;

    BondAngleMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
