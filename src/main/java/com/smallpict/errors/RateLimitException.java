package com.smallpict.errors;

import java.util.Map;

public class RateLimitException extends SmallPictException {
    private final Long retryAfterSeconds;

    public RateLimitException(String message) {
        this(message, null, null, null);
    }

    public RateLimitException(String message, Long retryAfterSeconds, String requestId, Map<String, Object> details) {
        this(message, null, retryAfterSeconds, requestId, details);
    }

    /** {@code code} is the server's error code; {@code null} keeps RATE_LIMIT_EXCEEDED. */
    public RateLimitException(String message, String code, Long retryAfterSeconds, String requestId, Map<String, Object> details) {
        super(message, code != null ? code : "RATE_LIMIT_EXCEEDED", 429, requestId, details, null);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
