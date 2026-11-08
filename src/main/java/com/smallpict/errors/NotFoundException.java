package com.smallpict.errors;

import java.util.Map;

public class NotFoundException extends SmallPictException {
    public NotFoundException(String message) {
        this(message, null, null);
    }

    public NotFoundException(String message, String requestId, Map<String, Object> details) {
        this(message, null, requestId, details);
    }

    /** {@code code} is the server's error code; {@code null} keeps the class default. */
    public NotFoundException(String message, String code, String requestId, Map<String, Object> details) {
        super(message, code != null ? code : "NOT_FOUND", 404, requestId, details, null);
    }
}
