package com.molview.app.model;

/** Active drawing mode. */
public enum Tool {
    SELECT("Select", "Select and move objects"),
    PAN("Pan", "Pan the drawing canvas"),
    ATOM("Atom", "Click: atom · Drag empty: chain · Drag atom past threshold: chain"),
    BOND("Bond", "Draw a bond; single-tool clicks cycle a bond single↔triple"),
    CHARGE_POS("Positive charge", "Add a formal positive charge"),
    CHARGE_NEG("Negative charge", "Add a formal negative charge"),
    PARTIAL_POS("Partial positive", "Add a δ+ partial charge"),
    PARTIAL_NEG("Partial negative", "Add a δ− partial charge"),
    LONE_PAIR("Lone pair", "Add a lone pair"),
    RADICAL("Radical", "Add a single unpaired electron"),
    ARROW_STRAIGHT("Reaction arrow", "Draw a straight reaction arrow; hold Shift to keep it horizontal"),
    ARROW_CURVED("Curved arrow", "Draw a curved electron-pair arrow"),
    ARROW_FISHHOOK("Fishhook", "Draw a single-electron fishhook arrow"),
    BRACKET_SQUARE("Square bracket", "Draw or enclose with [ ]"),
    BRACKET_ROUND("Round bracket", "Draw or enclose with ( )"),
    BRACKET_CURLY("Curly bracket", "Draw or enclose with { }"),
    TEXT("Text", "Place a text annotation"),
    ERASE("Erase", "Click an object to erase it");

    private final String displayName;
    private final String hint;

    Tool(String displayName, String hint) {
        this.displayName = displayName;
        this.hint = hint;
    }

    public String displayName() {
        return displayName;
    }

    public String hint() {
        return hint;
    }

    public BracketType bracketType() {
        return switch (this) {
            case BRACKET_SQUARE -> BracketType.SQUARE;
            case BRACKET_ROUND -> BracketType.ROUND;
            case BRACKET_CURLY -> BracketType.CURLY;
            default -> null;
        };
    }

    public ChargeKind chargeKind() {
        return switch (this) {
            case CHARGE_POS -> ChargeKind.POSITIVE;
            case CHARGE_NEG -> ChargeKind.NEGATIVE;
            case PARTIAL_POS -> ChargeKind.PARTIAL_POSITIVE;
            case PARTIAL_NEG -> ChargeKind.PARTIAL_NEGATIVE;
            default -> null;
        };
    }

    public ElectronKind electronKind() {
        return switch (this) {
            case LONE_PAIR -> ElectronKind.LONE_PAIR;
            case RADICAL -> ElectronKind.RADICAL;
            default -> null;
        };
    }
}
