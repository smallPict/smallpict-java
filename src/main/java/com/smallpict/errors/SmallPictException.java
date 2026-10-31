package com.smallpict.errors;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmallPictException extends RuntimeException {
    private static final Pattern KEY_PATTERN = Pattern.compile("sp_(test|sdk|wp)_[a-zA-Z0-9_-]{10,}");
    private static final Pattern SECRET_PATTERN = Pattern.compile("(?i)(sec|secret)_[a-zA-Z0-9_-]{8,}");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)Bearer\\s+[a-zA-Z0-9._-]+");

    private final String errorCode;
    private final Integer statusCode;
    private final String requestId;
    private final Map<String, Object> details;
    private final String upgradeUrl;

    public SmallPictException(String message) {
        this(message, "INTERNAL_ERROR", null, null, null, null);
    }

    public SmallPictException(
            String message,
            String errorCode,
            Integer statusCode,
            String requestId,
            Map<String, Object> details,
            Throwable cause) {
        super(sanitize(message), cause);
        this.errorCode = errorCode != null ? errorCode : "INTERNAL_ERROR";
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.details = details;
        Object url = details != null ? details.get("upgrade_url") : null;
        this.upgradeUrl = url instanceof String ? (String) url : null;
    }

    public static String sanitize(String msg) {
        if (msg == null || msg.isEmpty()) {
            return "";
        }

        Matcher keyMatcher = KEY_PATTERN.matcher(msg);
        StringBuffer sb = new StringBuffer();
        while (keyMatcher.find()) {
            String full = keyMatcher.group();
            String masked = full.length() > 14
                    ? full.substring(0, 10) + "..." + full.substring(full.length() - 4)
                    : full.substring(0, 6) + "...";
            keyMatcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        keyMatcher.appendTail(sb);
        String step1 = sb.toString();

        String step2 = SECRET_PATTERN.matcher(step1).replaceAll("***REDACTED***");
        return BEARER_PATTERN.matcher(step2).replaceAll("Bearer ***REDACTED***");
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public String getRequestId() {
        return requestId;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    /**
     * Dashboard page where the missing capacity is added ({@code details.upgrade_url}), or {@code null}.
     * Show it to the user; the SDK never opens or fetches it.
     */
    public String getUpgradeUrl() {
        return upgradeUrl;
    }

    @Override
    public String toString() {
        String statusStr = statusCode != null ? " HTTP " + statusCode : "";
        String reqStr = requestId != null ? " (Request ID: " + requestId + ")" : "";
        return "[" + errorCode + statusStr + "]: " + getMessage() + reqStr;
    }
}
