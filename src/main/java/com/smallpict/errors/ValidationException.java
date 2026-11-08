package com.smallpict.errors;

import java.util.Map;

public class ValidationException extends SmallPictException {
    public ValidationException(String message) {
        this(message, null, null);
    }

    public ValidationException(String message, String requestId, Map<String, Object> details) {
        this(message, null, requestId, details);
    }

    /** {@code code} is the server's error code; {@code null} keeps the class default. */
    public ValidationException(String message, String code, String requestId, Map<String, Object> details) {
        super(message, code != null ? code : "VALIDATION_FAILED", 400, requestId, details, null);
    }
}
