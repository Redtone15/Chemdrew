package com.molview.app.io;

import java.nio.file.Path;

/** All user-selectable export settings. */
public final class ExportOptions {
    private ExportFormat format = ExportFormat.PNG;
    private Path path = Path.of("exports", "structure.png");
    private int width = 1600;
    private int height = 1200;
    private boolean transparent = false;
    private String backgroundColor = "#ffffff";
    private boolean selectionOnly = false;
    private int dpi = 300;
    private PagePreset pagePreset = PagePreset.A4;
    private boolean landscape = false;
    private double customWidthPt = 595.28;
    private double customHeightPt = 841.89;
    private double marginPt = 36;

    public ExportFormat getFormat() {
        return format;
    }

    public void setFormat(ExportFormat format) {
        this.format = format;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = Math.max(64, Math.min(12000, width));
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = Math.max(64, Math.min(12000, height));
    }

    public boolean isTransparent() {
        return transparent;
    }

    public void setTransparent(boolean transparent) {
        this.transparent = transparent;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor == null ? "#ffffff" : backgroundColor;
    }

    public boolean isSelectionOnly() {
        return selectionOnly;
    }

    public void setSelectionOnly(boolean selectionOnly) {
        this.selectionOnly = selectionOnly;
    }

    public int getDpi() {
        return dpi;
    }

    public void setDpi(int dpi) {
        this.dpi = Math.max(72, Math.min(1200, dpi));
    }

    public PagePreset getPagePreset() {
        return pagePreset;
    }

    public void setPagePreset(PagePreset pagePreset) {
        this.pagePreset = pagePreset == null ? PagePreset.A4 : pagePreset;
    }

    public boolean isLandscape() {
        return landscape;
    }

    public void setLandscape(boolean landscape) {
        this.landscape = landscape;
    }

    public double getCustomWidthPt() {
        return customWidthPt;
    }

    public void setCustomWidthPt(double customWidthPt) {
        this.customWidthPt = Math.max(72, Math.min(2000, customWidthPt));
    }

    public double getCustomHeightPt() {
        return customHeightPt;
    }

    public void setCustomHeightPt(double customHeightPt) {
        this.customHeightPt = Math.max(72, Math.min(2000, customHeightPt));
    }

    public double getMarginPt() {
        return marginPt;
    }

    public void setMarginPt(double marginPt) {
        this.marginPt = Math.max(0, Math.min(300, marginPt));
    }

    public double pageWidthPt() {
        double base = pagePreset == PagePreset.CUSTOM ? customWidthPt : pagePreset.widthPt();
        double other = pagePreset == PagePreset.CUSTOM ? customHeightPt : pagePreset.heightPt();
        return landscape ? Math.max(base, other) : Math.min(base, other);
    }

    public double pageHeightPt() {
        double base = pagePreset == PagePreset.CUSTOM ? customWidthPt : pagePreset.widthPt();
        double other = pagePreset == PagePreset.CUSTOM ? customHeightPt : pagePreset.heightPt();
        return landscape ? Math.min(base, other) : Math.max(base, other);
    }

    public ExportOptions copy() {
        ExportOptions copy = new ExportOptions();
        copy.format = format;
        copy.path = path;
        copy.width = width;
        copy.height = height;
        copy.transparent = transparent;
        copy.backgroundColor = backgroundColor;
        copy.selectionOnly = selectionOnly;
        copy.dpi = dpi;
        copy.pagePreset = pagePreset;
        copy.landscape = landscape;
        copy.customWidthPt = customWidthPt;
        copy.customHeightPt = customHeightPt;
        copy.marginPt = marginPt;
        return copy;
    }
}
