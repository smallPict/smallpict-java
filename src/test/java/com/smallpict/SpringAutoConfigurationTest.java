package com.smallpict;

import com.smallpict.errors.TimeoutException;
import com.smallpict.models.OptimizeOptions;
import com.smallpict.spring.SmallPictAutoConfiguration;
import com.smallpict.spring.SmallPictProperties;
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

/** The auto-configuration must pass every property to the client, including the poll and wait options. */
class SpringAutoConfigurationTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("PUT".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                }
                if (request.getPath().startsWith("/v1/optimize/status")) {
                    return new MockResponse().setHeader("Content-Type", "application/json")
                            .setBody("{\"job_id\":\"job_1\",\"status\":\"processing\"}");
                }
                return new MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("{\"job_id\":\"job_1\",\"status\":\"queued\",\"upload_url\":\"" + server.url("/upload/obj")
                                + "\",\"object_key\":\"k\",\"bucket\":\"b\"}");
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void pollIntervalAndWaitTimeoutReachTheClient() {
        SmallPictProperties props = new SmallPictProperties();
        props.setApiKey("sp_sdk_test");
        props.setSecretKey("sec_test");
        props.setBaseUrl(server.url("/").toString());
        props.setPollInterval(Duration.ofMillis(5));
        props.setWaitTimeout(Duration.ofMillis(100));

        SmallPictClient client = new SmallPictAutoConfiguration().smallPictClient(props);

        long started = System.nanoTime();
        TimeoutException e = assertThrows(TimeoutException.class,
                () -> client.optimize("img".getBytes(), OptimizeOptions.builder().mimeType("image/png").build()));
        assertTrue(e.getMessage().contains("job_1"), e.getMessage());
        assertTrue(System.nanoTime() - started < 5_000_000_000L, "the default 120 s wait_timeout was used");
        assertTrue(server.getRequestCount() >= 3, "expected POST, PUT and at least one poll");
    }
}
