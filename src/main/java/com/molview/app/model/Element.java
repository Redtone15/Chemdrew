package com.molview.app.model;

/** Immutable periodic-table element metadata. */
public record Element(int atomicNumber, String symbol, String name,
                      int group, int period, String category) {
}
