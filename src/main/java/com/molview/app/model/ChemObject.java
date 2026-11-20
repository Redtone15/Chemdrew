package com.molview.app.model;

import java.util.Objects;

/** Base class for every independently selectable object on the drawing. */
public abstract class ChemObject {
    private String id;
    private boolean selected;

    protected ChemObject(String id) {
        this.id = Objects.requireNonNullElseGet(id, Ids::next);
    }

    public final String getId() {
        return id;
    }

    public final void setId(String id) {
        this.id = Objects.requireNonNull(id);
    }

    public final boolean isSelected() {
        return selected;
    }

    public final void setSelected(boolean selected) {
        this.selected = selected;
    }

    public abstract Bounds bounds();

    public abstract void translate(double dx, double dy);

    /**
     * Hit test in world coordinates. Implementations include an intuitive touch
     * tolerance so thin lines and dots remain easy to select.
     */
    public abstract boolean hitTest(double x, double y, double tolerance);

    /**
     * @param preserveId true for undo snapshots, false when creating a paste copy
     */
    public abstract ChemObject copy(boolean preserveId);

    /** A short, human-readable name used by the inspector and status bar. */
    public abstract String typeName();

    protected final <T extends ChemObject> T copyBase(T target, boolean preserveId) {
        if (preserveId) {
            target.setId(id);
        }
        target.setSelected(selected);
        return target;
    }

    @Override
    public String toString() {
        return typeName() + "[" + id + "]";
    }
}
