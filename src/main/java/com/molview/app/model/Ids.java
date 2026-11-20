package com.molview.app.model;

import java.util.UUID;

/** Short random identifiers keep project files readable and stable. */
public final class Ids {
    private Ids() {
    }

    public static String next() {
        return next("o");
    }

    public static String next(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
