package com.molview.app.model;

public enum BracketType {
    SQUARE("[", "]"),
    ROUND("(", ")"),
    CURLY("{", "}");

    private final String open;
    private final String close;

    BracketType(String open, String close) {
        this.open = open;
        this.close = close;
    }

    public String open() {
        return open;
    }

    public String close() {
        return close;
    }

    @Override
    public String toString() {
        return switch (this) {
            case SQUARE -> "Square  [ ]";
            case ROUND -> "Round  ( )";
            case CURLY -> "Curly  { }";
        };
    }
}
