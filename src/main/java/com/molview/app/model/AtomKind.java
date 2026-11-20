package com.molview.app.model;

public enum AtomKind {
    ELEMENT,
    R_GROUP,
    X_GROUP;

    public boolean isGeneric() {
        return this != ELEMENT;
    }
}
