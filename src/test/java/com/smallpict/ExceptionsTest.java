package com.smallpict;

import com.smallpict.errors.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionsTest {

    @Test
    void testSanitizeMessageMasksApiKeyAndSecret() {
        String raw = "Invalid key sp_sdk_1234567890abcdef1234567890abcdef with sec_secret123456";
        String sanitized = SmallPictException.sanitize(raw);

        assertFalse(sanitized.contains("sp_sdk_1234567890abcdef1234567890abcdef"));
        assertFalse(sanitized.contains("sec_secret123456"));
        assertTrue(sanitized.contains("sp_sdk_123...cdef"));
        assertTrue(sanitized.contains("***REDACTED***"));
    }

    @Test
    void testToStringRedaction() {
        SmallPictException ex = new AuthenticationException("Revoked key sp_test_11223344556677889900", "req_001", null);
        String str = ex.toString();

        assertFalse(str.contains("sp_test_11223344556677889900"));
        assertTrue(str.contains("UNAUTHORIZED HTTP 401"));
        assertTrue(str.contains("Request ID: req_001"));
    }

    @Test
    void testExceptionHierarchy() {
        assertEquals(400, new ValidationException("Bad params").getStatusCode());
        assertEquals(401, new AuthenticationException("Unauthorized").getStatusCode());
        assertEquals(402, new QuotaExceededException("Full").getStatusCode());
        assertEquals(403, new PermissionDeniedException("Forbidden").getStatusCode());
        assertEquals(404, new NotFoundException("Not found").getStatusCode());
        assertEquals(429, new RateLimitException("Slow").getStatusCode());
        assertEquals(500, new ServerException("Crash", 500, null, null).getStatusCode());
        assertEquals(408, new TimeoutException("Timed out").getStatusCode());
    }

    @Test
    void sanitizeMasksServerIssuedSecrets() {
        String secret = "sps_" + "ab12".repeat(16);
        assertFalse(SmallPictException.sanitize("bad key " + secret).contains(secret));
    }
}
