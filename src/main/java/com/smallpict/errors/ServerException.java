package com.smallpict.errors;

import java.util.Map;

public class ServerException extends SmallPictException {
    public ServerException(String message, int statusCode, String requestId, Map<String, Object> details) {
        this(message, null, statusCode, requestId, details);
    }

    /** {@code code} is the server's error code; {@code null} keeps INTERNAL_ERROR. */
    public ServerException(String message, String code, int statusCode, String requestId, Map<String, Object> details) {
        super(message, code != null ? code : "INTERNAL_ERROR", statusCode, requestId, details, null);
    }
}
