package com.molview.app.model;

public enum BracketLabelPosition {
    TOP_RIGHT,
    BOTTOM_RIGHT,
    TOP_LEFT,
    BOTTOM_LEFT;

    @Override
    public String toString() {
        return switch (this) {
            case TOP_RIGHT -> "Top right";
            case BOTTOM_RIGHT -> "Bottom right";
            case TOP_LEFT -> "Top left";
            case BOTTOM_LEFT -> "Bottom left";
        };
    }
}
