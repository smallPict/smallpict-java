package com.smallpict.models;

import com.fasterxml.jackson.annotation.JsonValue;

/** Compression mode of {@code POST /v1/optimize} ({@code options.mode}). The server defaults to {@link #LOSSY} and checks the mode against the plan. */
public enum CompressionMode {
    LOSSY("lossy"),
    EXTREME("extreme"),
    LOSSLESS("lossless");

    private final String value;

    CompressionMode(String value) {
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
