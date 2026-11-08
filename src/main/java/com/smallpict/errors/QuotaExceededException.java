package com.smallpict.errors;

import java.util.Map;

public class QuotaExceededException extends SmallPictException {
    public QuotaExceededException(String message) {
        this(message, null, null);
    }

    public QuotaExceededException(String message, String requestId, Map<String, Object> details) {
        this(message, null, requestId, details);
    }

    /** {@code code} is the server's error code; {@code null} keeps the class default. */
    public QuotaExceededException(String message, String code, String requestId, Map<String, Object> details) {
        super(message, code != null ? code : "QUOTA_EXCEEDED", 402, requestId, details, null);
    }
}
