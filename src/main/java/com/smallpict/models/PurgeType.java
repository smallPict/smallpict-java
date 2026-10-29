package com.smallpict.models;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PurgeType {
    URL("url"),
    /** One upload folder; use {@code purgeCdn(urls, PurgeType.FOLDER, folder)}. */
    FOLDER("folder"),
    ALL("all");

    private final String value;

    PurgeType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
