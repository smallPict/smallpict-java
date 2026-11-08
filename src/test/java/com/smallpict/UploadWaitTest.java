package com.smallpict;

import com.smallpict.errors.QuotaExceededException;
import com.smallpict.errors.SmallPictException;
import com.smallpict.errors.TimeoutException;
import com.smallpict.models.OptimizeOptions;
import com.smallpict.models.OptimizeResult;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** SDK_SPEC 3d: optimize() POSTs, PUTs the bytes to upload_url, then polls the job status. */
class UploadWaitTest {
    private static final byte[] BYTES = "fake image bytes".getBytes();
    private MockWebServer server;
    private SmallPictClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = SmallPictClient.builder()
                .apiKey("sp_sdk_test")
                .secretKey("sec_test")
                .baseUrl(server.url("/").toString())
                .pollInterval(Duration.ofMillis(5))
                .waitTimeout(Duration.ofMillis(200))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private String queued() {
        return "{\"job_id\":\"job_1\",\"status\":\"queued\",\"upload_url\":\"" + server.url("/upload/obj?sig=abc")
                + "\",\"object_key\":\"k\",\"bucket\":\"b\"}";
    }

    private void enqueueJson(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body));
    }

    private OptimizeResult optimizePng() {
        return client.optimize(BYTES, OptimizeOptions.builder().filename("a.png").mimeType("image/png").build());
    }

    @Test
    void uploadsThenPollsUntilCompleted() throws Exception {
        enqueueJson(200, queued());
        server.enqueue(new MockResponse().setResponseCode(200));
        enqueueJson(200, "{\"job_id\":\"job_1\",\"status\":\"processing\"}");
        enqueueJson(200, "{\"job_id\":\"job_1\",\"status\":\"completed\",\"url\":\"https://cdn.smallpict.app/o/a.webp\","
                + "\"format\":\"webp\",\"bytes_saved\":4,\"mode_applied\":\"lossy\",\"quality_applied\":72}");

        OptimizeResult r = optimizePng();

        RecordedRequest post = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("POST", post.getMethod());
        assertEquals("/v1/optimize", post.getPath());

        RecordedRequest put = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(put, "no PUT sent");
        assertEquals("PUT", put.getMethod());
        assertEquals("/upload/obj?sig=abc", put.getPath());
        assertEquals("image/png", put.getHeader("Content-Type"));
        assertEquals(String.valueOf(BYTES.length), put.getHeader("Content-Length"));
        for (String h : new String[]{"X-API-Key", "X-Timestamp", "X-Signature", "Authorization", "Idempotency-Key"}) {
            assertNull(put.getHeader(h), h);
        }
        assertArrayEquals(BYTES, put.getBody().readByteArray());

        RecordedRequest poll = server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("GET", poll.getMethod());
        assertEquals("/v1/optimize/status?job_id=job_1", poll.getPath());
        assertNotNull(poll.getHeader("X-Signature"));
        assertEquals(4, server.getRequestCount());

        assertEquals("job_1", r.getJobId());
        assertEquals("completed", r.getStatus());
        assertEquals("https://cdn.smallpict.app/o/a.webp", r.getUrl());
        assertEquals("webp", r.getFormat());
        assertEquals(BYTES.length, r.getOriginalSize());
        assertEquals(BYTES.length - 4, r.getCompressedSize());
        assertEquals(4, r.getBytesSaved());
        assertEquals(25.0, r.getSavingsPercentage());
        assertEquals("lossy", r.getModeApplied());
        assertEquals(72, r.getQualityApplied());
        assertNull(r.getFolder());
    }

    @Test
    void failedJobRaisesJobFailed() {
        enqueueJson(200, queued());
        server.enqueue(new MockResponse().setResponseCode(200));
        enqueueJson(200, "{\"job_id\":\"job_1\",\"status\":\"failed\",\"error\":{\"message\":\"client upload never arrived\"}}");

        Throwable t = assertThrows(Throwable.class, this::optimizePng);
        SmallPictException e = assertInstanceOf(SmallPictException.class, t);
        assertEquals("job_failed", e.getErrorCode());
        assertEquals("client upload never arrived", e.getMessage());
        assertEquals("job_1", e.getDetails().get("job_id"));
    }

    @Test
    void stillRunningAfterWaitTimeoutRaisesTimeout() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest req) {
                if ("POST".equals(req.getMethod())) {
                    return new MockResponse().setResponseCode(200).setBody(queued());
                }
                if ("PUT".equals(req.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(200).setBody("{\"job_id\":\"job_1\",\"status\":\"processing\"}");
            }
        });

        Throwable t = assertThrows(Throwable.class, this::optimizePng);
        TimeoutException e = assertInstanceOf(TimeoutException.class, t);
        assertTrue(e.getMessage().contains("job_1"), e.getMessage());
        assertTrue(e.getMessage().contains("getJobStatus"), e.getMessage());
    }

    @Test
    void refusedUploadRaisesUploadFailed() {
        enqueueJson(200, queued());
        server.enqueue(new MockResponse().setResponseCode(403).setBody("SignatureDoesNotMatch"));

        Throwable t = assertThrows(Throwable.class, this::optimizePng);
        SmallPictException e = assertInstanceOf(SmallPictException.class, t);
        assertEquals("upload_failed", e.getErrorCode());
        assertEquals(403, e.getStatusCode());
        assertTrue(e.getMessage().contains("SignatureDoesNotMatch"), e.getMessage());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    void fileSourceErrorsAreNotRewrapped() throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("sp", ".png");
        java.nio.file.Files.write(f, BYTES);
        enqueueJson(200, queued());
        server.enqueue(new MockResponse().setResponseCode(200));
        enqueueJson(200, "{\"job_id\":\"job_1\",\"status\":\"failed\",\"error\":{\"message\":\"boom\"}}");

        Throwable t = assertThrows(Throwable.class,
                () -> client.optimize(f, OptimizeOptions.builder().mimeType("image/png").build()));
        assertEquals("job_failed", assertInstanceOf(SmallPictException.class, t).getErrorCode());
        java.nio.file.Files.delete(f);
    }

    @Test
    void syncMethodsThrowTheTypedException() {
        enqueueJson(402, "{\"error\":{\"code\":\"QUOTA_EXCEEDED\",\"message\":\"Quota exhausted\"}}");
        QuotaExceededException e = assertThrows(QuotaExceededException.class, this::optimizePng);
        assertEquals("QUOTA_EXCEEDED", e.getErrorCode());
    }
}
