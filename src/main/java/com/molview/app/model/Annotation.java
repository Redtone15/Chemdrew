package com.molview.app.model;

/**
 * Base class for annotations that can optionally remember the atom they belong
 * to. The link is used when moving/deleting atoms, while coordinates stay
 * independent so the label can be placed exactly where the chemist wants it.
 */
public abstract class Annotation extends ChemObject {
    private String attachedAtomId;

    protected Annotation(String id) {
        super(id);
    }

    public String getAttachedAtomId() {
        return attachedAtomId;
    }

    public void setAttachedAtomId(String attachedAtomId) {
        this.attachedAtomId = attachedAtomId;
    }

    public boolean isAttached() {
        return attachedAtomId != null;
    }

    protected final <T extends Annotation> T copyAnnotation(T target, boolean preserveId) {
        copyBase(target, preserveId);
        target.setAttachedAtomId(attachedAtomId);
        return target;
    }
}
