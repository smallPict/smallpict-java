package com.smallpict;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smallpict.errors.PermissionDeniedException;
import com.smallpict.errors.ValidationException;
import com.smallpict.models.CompressionMode;
import com.smallpict.models.JobStatusResult;
import com.smallpict.models.OptimizeOptions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Mapping cases of smallpict-sdk-shared/compression-modes.json, plus job status and error parsing. */
@SuppressWarnings("deprecation")
class CompressionModeTest {
    private static final String OK_BODY = "{\"job_id\":\"job_1\",\"status\":\"processing\"}";
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
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private JsonNode sentOptions(Consumer<OptimizeOptions.Builder> input) throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(OK_BODY));
        OptimizeOptions.Builder b = OptimizeOptions.builder();
        input.accept(b);
        client.optimize("img".getBytes(), b.build());
        JsonNode options = mapper.readTree(server.takeRequest().getBody().readUtf8()).get("options");
        assertFalse(options.has("lossless"), "lossless must never be sent");
        return options;
    }

    @Test
    void noModeNoFlag() throws Exception {
        assertFalse(sentOptions(b -> {}).has("mode"));
    }

    @Test
    void qualityOmittedWhenUnset() throws Exception {
        assertFalse(sentOptions(b -> b.mode(CompressionMode.EXTREME)).has("quality"));
        assertNull(OptimizeOptions.builder().build().getQuality());
    }

    @Test
    void qualitySentWhenSet() throws Exception {
        assertEquals(55, sentOptions(b -> b.mode(CompressionMode.EXTREME).quality(55)).get("quality").asInt());
    }

    @Test
    void modeOnly() throws Exception {
        assertEquals("extreme", sentOptions(b -> b.mode(CompressionMode.EXTREME)).get("mode").asText());
    }

    @Test
    void modeAuto() throws Exception {
        assertEquals("auto", sentOptions(b -> b.mode(CompressionMode.AUTO)).get("mode").asText());
    }

    @Test
    void deprecatedFlagOnly() throws Exception {
        assertEquals("lossless", sentOptions(b -> b.lossless(true)).get("mode").asText());
    }

    @Test
    void flagFalseIsIgnored() throws Exception {
        assertFalse(sentOptions(b -> b.lossless(false)).has("mode"));
    }

    @Test
    void flagAgreesWithMode() throws Exception {
        assertEquals("lossless", sentOptions(b -> b.mode(CompressionMode.LOSSLESS).lossless(true)).get("mode").asText());
    }

    @Test
    void flagContradictsMode() {
        assertThrows(ValidationException.class,
                () -> client.optimize("img".getBytes(), OptimizeOptions.builder().mode(CompressionMode.LOSSY).lossless(true).build()));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void jobStatusParsesModeAndQualityApplied() throws Exception {
        JobStatusResult r = mapper.readValue(
                "{\"job_id\":\"j\",\"status\":\"completed\",\"mode_applied\":\"future_mode\",\"quality_applied\":68}",
                JobStatusResult.class);
        assertEquals("future_mode", r.getModeApplied());
        assertEquals(68, r.getQualityApplied());

        JobStatusResult old = mapper.readValue("{\"job_id\":\"j\",\"status\":\"completed\"}", JobStatusResult.class);
        assertNull(old.getModeApplied());
        assertNull(old.getQualityApplied());
    }

    @Test
    void modeNotInPlanKeepsDetails() {
        server.enqueue(new MockResponse().setResponseCode(403).setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":\"mode_not_in_plan\",\"message\":\"Lossless is not in your plan\","
                        + "\"details\":{\"mode\":\"lossless\",\"allowed_modes\":[\"lossy\",\"extreme\"]}}}"));

        Throwable t = assertThrows(Throwable.class,
                () -> client.optimize("img".getBytes(), OptimizeOptions.builder().mode(CompressionMode.LOSSLESS).build()));
        if (t instanceof CompletionException) {
            t = t.getCause();
        }
        PermissionDeniedException e = assertInstanceOf(PermissionDeniedException.class, t);
        assertEquals("Lossless is not in your plan", e.getMessage());
        assertEquals("lossless", e.getDetails().get("mode"));
        assertEquals(List.of("lossy", "extreme"), e.getDetails().get("allowed_modes"));
    }
}
