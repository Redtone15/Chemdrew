package com.molview.app.render;

/** Visual options for screen and file rendering. */
public record RenderSettings(boolean showCarbonLabels, boolean showSelection, double zoom) {
    public static RenderSettings screen(boolean showCarbonLabels, double zoom) {
        return new RenderSettings(showCarbonLabels, true, zoom);
    }

    public static RenderSettings export(boolean showCarbonLabels) {
        return new RenderSettings(showCarbonLabels, false, 1);
    }
}
