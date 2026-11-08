package com.smallpict;

import com.smallpict.errors.ValidationException;
import com.smallpict.models.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmallPictClientTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void testMissingApiKeyThrowsValidationException() {
        assertThrows(ValidationException.class, () -> new SmallPictClient(""));
    }

    @Test
    void testOptimizeSuccess() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"job_id\":\"job_java_123\",\"status\":\"queued\",\"upload_url\":\"" + server.url("/upload") + "\"}"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"job_id\":\"job_java_123\",\"status\":\"completed\","
                        + "\"url\":\"https://cdn.smallpict.app/opt/hero.avif\",\"format\":\"avif\",\"bytes_saved\":17}"));

        SmallPictClient client = SmallPictClient.builder()
                .apiKey("sp_sdk_test_1234567890")
                .secretKey("sec_test_secret_123")
                .baseUrl(server.url("/").toString())
                .pollInterval(java.time.Duration.ofMillis(1))
                .build();

        OptimizeResult result = client.optimize(
                "fake image raw bytes".getBytes(),
                OptimizeOptions.builder().format(ImageFormat.AVIF).quality(85).build()
        );

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/optimize", req.getPath());
        assertEquals("sp_sdk_test_1234567890", req.getHeader("X-API-Key"));
        assertNotNull(req.getHeader("X-Signature"));
        assertNotNull(req.getHeader("X-Timestamp"));
        assertNotNull(req.getHeader("Idempotency-Key"));

        assertEquals("job_java_123", result.getJobId());
        assertEquals("completed", result.getStatus());
        assertEquals("https://cdn.smallpict.app/opt/hero.avif", result.getUrl());
        assertEquals(85.0, result.getSavingsPercentage()); // 17 of 20 bytes
    }

    @Test
    void testGetQuota() throws InterruptedException {
        String json = """
        {
            "plan": "api_velocity",
            "bytes_used": 5000000,
            "quota_limit": 10000000,
            "quota_percentage": 50.0,
            "active_keys_count": 3
        }
        """;
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json));

        SmallPictClient client = SmallPictClient.builder()
                .apiKey("sp_sdk_test")
                .secretKey("sec_test")
                .baseUrl(server.url("/").toString())
                .build();

        QuotaResponse quota = client.getQuota();
        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/v1/quota", req.getPath());

        assertEquals("api_velocity", quota.getPlan());
        assertEquals(5000000L, quota.getBytesUsed());
        assertEquals(50.0, quota.getQuotaPercentage());
    }

    @Test
    void testPurgeCdn() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Purge accepted\"}"));

        SmallPictClient client = SmallPictClient.builder()
                .apiKey("sp_sdk_test")
                .secretKey("sec_test")
                .baseUrl(server.url("/").toString())
                .build();

        PurgeResponse res = client.purgeCdn(List.of("https://cdn.smallpict.app/opt/hero.avif"), PurgeType.URL);
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/v1/purge", req.getPath());
        assertEquals("Purge accepted", res.getMessage());
    }

    @Test
    void testValidateKey() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"plan\":\"free\"}"));
        SmallPictClient validClient = SmallPictClient.builder().apiKey("sp_sdk_valid").secretKey("sec_test").baseUrl(server.url("/").toString()).build();
        assertTrue(validClient.validateKey());

        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":{\"code\":\"UNAUTHORIZED\"}}"));
        SmallPictClient invalidClient = SmallPictClient.builder().apiKey("sp_sdk_invalid").secretKey("sec_test").baseUrl(server.url("/").toString()).build();
        assertFalse(invalidClient.validateKey());
    }

    @Test
    void testFallbackPassthroughOnQuotaExceeded() {
        server.enqueue(new MockResponse()
                .setResponseCode(402)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":\"QUOTA_EXCEEDED\",\"message\":\"Quota exhausted\"}}"));

        SmallPictClient client = SmallPictClient.builder()
                .apiKey("sp_sdk_test")
                .secretKey("sec_test")
                .baseUrl(server.url("/").toString())
                .fallbackMode(FallbackMode.PASSTHROUGH)
                .build();

        byte[] rawData = "raw binary image content".getBytes();
        OptimizeResult result = client.optimize(rawData, OptimizeOptions.builder().filename("photo.jpg").mimeType("image/jpeg").build());

        assertEquals("fallback-passthrough", result.getJobId());
        assertEquals("completed", result.getStatus());
        assertEquals(0.0, result.getSavingsPercentage());
        assertArrayEquals(rawData, result.getData());
    }

    @Test
    void builderWithoutSecretKeyIsValidationError() {
        com.smallpict.errors.ValidationException e = assertThrows(com.smallpict.errors.ValidationException.class,
                () -> SmallPictClient.builder().apiKey("sp_sdk_test").secretKey("").baseUrl(server.url("/").toString()).build());
        assertTrue(e.getMessage().contains("secret key"), e.getMessage());
    }

    @Test
    void retrySendsNewNonceAndSameIdempotencyKey() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(503).setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"try again\"}}"));
        server.enqueue(new MockResponse().setResponseCode(202).setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Purge accepted\"}"));
        SmallPictClient client = SmallPictClient.builder()
                .apiKey("sp_sdk_test")
                .secretKey("sec_test")
                .baseUrl(server.url("/").toString())
                .maxRetries(1)
                .build();

        client.purgeCdn(List.of("https://cdn.smallpict.app/opt/hero.avif"), PurgeType.URL);

        RecordedRequest first = server.takeRequest();
        RecordedRequest second = server.takeRequest();
        assertNotNull(first.getHeader("X-Nonce"), "no X-Nonce on the first attempt");
        assertNotEquals(first.getHeader("X-Nonce"), second.getHeader("X-Nonce"));
        assertEquals(first.getHeader("Idempotency-Key"), second.getHeader("Idempotency-Key"));
    }
}
