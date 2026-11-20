package com.molview.app.model;

public enum ChargeKind {
    POSITIVE("+", false),
    NEGATIVE("−", false),
    PARTIAL_POSITIVE("δ+", true),
    PARTIAL_NEGATIVE("δ−", true);

    private final String label;
    private final boolean partial;

    ChargeKind(String label, boolean partial) {
        this.label = label;
        this.partial = partial;
    }

    public String label() {
        return label;
    }

    public boolean isPartial() {
        return partial;
    }

    /** Formal charge contribution used by MOL/SMILES exporters. */
    public int formalCharge() {
        return switch (this) {
            case POSITIVE -> 1;
            case NEGATIVE -> -1;
            default -> 0;
        };
    }

    @Override
    public String toString() {
        return partial ? "Partial " + label : "Formal " + label;
    }
}
