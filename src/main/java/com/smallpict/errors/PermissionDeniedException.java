package com.smallpict.errors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PermissionDeniedException extends SmallPictException {
    private final List<String> allowedModes;

    public PermissionDeniedException(String message) {
        this(message, null, null);
    }

    public PermissionDeniedException(String message, String requestId, Map<String, Object> details) {
        this(message, null, requestId, details);
    }

    /** {@code code} is the server's error code; {@code null} keeps the class default. */
    public PermissionDeniedException(String message, String code, String requestId, Map<String, Object> details) {
        super(message, code != null ? code : "FORBIDDEN", 403, requestId, details, null);
        Object modes = details != null ? details.get("allowed_modes") : null;
        if (modes instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object m : (List<?>) modes) {
                if (m instanceof String) {
                    list.add((String) m);
                }
            }
            this.allowedModes = Collections.unmodifiableList(list);
        } else {
            this.allowedModes = null;
        }
    }

    /** Modes the plan allows, from {@code details.allowed_modes} of {@code mode_not_in_plan}; otherwise {@code null}. */
    public List<String> getAllowedModes() {
        return allowedModes;
    }
}
