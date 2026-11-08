package com.smallpict;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smallpict.errors.RateLimitException;
import com.smallpict.errors.ValidationException;
import com.smallpict.models.JobStatusResult;
import com.smallpict.models.OptimizeOptions;
import com.smallpict.models.OptimizeResult;
import com.smallpict.models.PurgeType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** FLD-03: upload folder on optimize, folder purges, purge_rate_limited not retried. */
class FolderTest {
    private final ObjectMapper mapper = new ObjectMapper();
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
                .maxRetries(3)
                .pollInterval(java.time.Duration.ofMillis(1))
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueueJson(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body));
    }

    /** Queued response, accepted PUT, then the given job status. */
    private void enqueueJob(String completedStatus) {
        enqueueJson(200, "{\"job_id\":\"job_1\",\"status\":\"queued\",\"upload_url\":\"" + server.url("/upload") + "\"}");
        server.enqueue(new MockResponse().setResponseCode(200));
        enqueueJson(200, completedStatus);
    }

    private JsonNode sentBody() throws Exception {
        return mapper.readTree(server.takeRequest().getBody().readUtf8());
    }

    @Test
    void optimizeSendsFolderAndSurfacesIt() throws Exception {
        enqueueJob("{\"job_id\":\"job_1\",\"status\":\"completed\",\"folder\":\"blog/2026\"}");
        OptimizeResult r = client.optimize("img".getBytes(), OptimizeOptions.builder().folder("Blog/2026").build());
        assertEquals("Blog/2026", sentBody().get("options").get("folder").asText());
        assertEquals("blog/2026", r.getFolder());
    }

    @Test
    void optimizeOmitsFolderWhenUnset() throws Exception {
        enqueueJob("{\"job_id\":\"job_1\",\"status\":\"completed\"}");
        OptimizeResult r = client.optimize("img".getBytes(), OptimizeOptions.builder().build());
        assertFalse(sentBody().get("options").has("folder"));
        assertNull(r.getFolder());
    }

    @Test
    void jobStatusParsesFolder() throws Exception {
        assertEquals("blog/2026", mapper.readValue("{\"job_id\":\"j\",\"folder\":\"blog/2026\"}", JobStatusResult.class).getFolder());
        assertNull(mapper.readValue("{\"job_id\":\"j\"}", JobStatusResult.class).getFolder());
    }

    private static final String[] MALFORMED = {"", "a//b", "/a", "a/", "../x", "a/../b", "a/b/c/d"};

    @Test
    void optimizeRejectsMalformedFolderWithoutRequest() {
        for (String folder : MALFORMED) {
            Throwable t = assertThrows(Throwable.class,
                    () -> client.optimize("img".getBytes(), OptimizeOptions.builder().folder(folder).build()), folder);
            assertInstanceOf(ValidationException.class, t, folder);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void purgeRejectsMalformedFolderWithoutRequest() {
        for (String folder : MALFORMED) {
            Throwable t = assertThrows(Throwable.class, () -> client.purgeCdn(null, PurgeType.FOLDER, folder), folder);
            assertInstanceOf(ValidationException.class, t, folder);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void purgeFolderRequiresFolder() {
        for (String missing : new String[] {null, "", "  "}) {
            Throwable t = assertThrows(Throwable.class, () -> client.purgeCdn(null, PurgeType.FOLDER, missing));
            assertInstanceOf(ValidationException.class, t);
        }
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void purgeFolderBodyShape() throws Exception {
        enqueueJson(202, "{\"message\":\"Purge job enqueued successfully\"}");
        client.purgeCdn(null, PurgeType.FOLDER, "products/shoes");
        JsonNode body = sentBody();
        assertEquals("folder", body.get("purge_type").asText());
        assertEquals("products/shoes", body.get("folder").asText());
    }

    @Test
    void purgeUrlAndAllNeverSendFolder() throws Exception {
        enqueueJson(202, "{\"message\":\"ok\"}");
        enqueueJson(202, "{\"message\":\"ok\"}");
        client.purgeCdn(List.of("https://cdn.smallpict.app/c/j.avif"), PurgeType.URL, "ignored");
        client.purgeCdn(null, PurgeType.ALL);
        JsonNode url = sentBody();
        assertEquals("url", url.get("purge_type").asText());
        assertFalse(url.has("folder"));
        JsonNode all = sentBody();
        assertEquals("all", all.get("purge_type").asText());
        assertFalse(all.has("folder"));
    }

    @Test
    void serverInvalidFolderIsValidationWithServerMessage() {
        enqueueJson(400, "{\"error\":{\"code\":\"invalid_folder\",\"message\":\"folder segment too long\"}}");
        Throwable t = assertThrows(Throwable.class,
                () -> client.optimize("img".getBytes(), OptimizeOptions.builder().folder("ok").build()));
        ValidationException e = assertInstanceOf(ValidationException.class, t);
        assertEquals("folder segment too long", e.getMessage());
    }

    @Test
    void purgeRateLimitedIsNotRetried() {
        enqueueJson(429, "{\"error\":{\"code\":\"purge_rate_limited\",\"message\":\"Too many folder purges\"}}");
        enqueueJson(202, "{\"message\":\"ok\"}");
        Throwable t = assertThrows(Throwable.class, () -> client.purgeCdn(null, PurgeType.FOLDER, "a"));
        RateLimitException e = assertInstanceOf(RateLimitException.class, t);
        assertEquals("Too many folder purges", e.getMessage());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void otherRateLimitIsStillRetried() {
        enqueueJson(429, "{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"slow down\"}}");
        enqueueJson(202, "{\"message\":\"ok\"}");
        assertEquals("ok", client.purgeCdn(null, PurgeType.FOLDER, "a").getMessage());
        assertEquals(2, server.getRequestCount());
    }
}
