package com.molview.app.model;

/** Persisted canvas view and drawing preferences. */
public record ViewState(double zoom, double offsetX, double offsetY,
                        boolean showGrid, boolean showCarbonLabels, boolean snapToGrid) {
    public static ViewState defaults() {
        return new ViewState(1.0, 0, 0, true, false, true);
    }
}
