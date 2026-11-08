package com.smallpict.errors;

import java.util.Map;

public class AuthenticationException extends SmallPictException {
    public AuthenticationException(String message) {
        this(message, null, null);
    }

    public AuthenticationException(String message, String requestId, Map<String, Object> details) {
        this(message, null, requestId, details);
    }

    /** {@code code} is the server's error code; {@code null} keeps the class default. */
    public AuthenticationException(String message, String code, String requestId, Map<String, Object> details) {
        super(message, code != null ? code : "UNAUTHORIZED", 401, requestId, details, null);
    }
}
